package dev.horizon.pyrite.identity;

import static dev.horizon.basepoint.expression.Expression.add;
import static dev.horizon.basepoint.expression.Expression.now;

import com.google.protobuf.ByteString;
import dev.horizon.cinder.Database;
import dev.horizon.cinder.DuplicateKeyException;
import dev.horizon.pyrite.AuthenticationOptions;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.authentication.v1.ProviderAccount;
import dev.horizon.pyrite.authentication.v1.RecoveryCodes;
import dev.horizon.pyrite.key.KeyService;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.storage.FactorBackend;
import dev.horizon.pyrite.storage.ProviderLinkBackend;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.grpc.common.GrpcStatus;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/**
 * The factors of accounts, each under its account: a password, a TOTP secret, a passkey, a set of recovery codes, or a
 * linked account at a provider. An exchange enrols a factor as pending, and activates it when the exchange ends; a
 * pending factor that no exchange activates is purged after the lifetime of exchanges.
 *
 * <p>
 * A password is kept as its Argon2id hash, with the Argon2id options of authentication. A TOTP secret is encrypted with
 * the encryption key of the options, with the factor's name as the associated data, and its counter is the last time
 * step that a code matched, so a code works once. A passkey keeps its WebAuthn credential, its id, its signature
 * counter, and whether its authenticator verifies the user. Recovery codes are ten codes that each work once in place
 * of a second factor; the factor keeps a JSON array of the MAC of each code that is left, which the MAC key of the
 * options signs, never the codes. A provider factor keeps the provider and the id of the account at it, as
 * {@code google:1093...}, and no account at a provider is linked to two accounts.
 */
public final class FactorService {

  private static final String FACTORS = "factors/";
  private static final String DECOY = "from the moment i understood the weakness of my flesh";
  private static final int RECOVERY_CODES = 10;
  private static final int RECOVERY_CODE_BYTES = 10;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final FactorBackend factors;
  private final ProviderLinkBackend links;
  private final Vertx vertx;
  private final AuthenticationOptions options;
  private final KeyService keys;
  private final WebAuthn webAuthn;

  public FactorService(Database database, Vertx vertx, AuthenticationOptions options, KeyService keys) {
    this.factors = new FactorBackend(database);
    this.links = new ProviderLinkBackend(database);
    this.vertx = vertx;
    this.options = options;
    this.keys = keys;
    this.webAuthn = new WebAuthn(options.getRelyingPartyOptions());
  }

  /**
   * Reads the factors of an account.
   *
   * @param account the name of the account
   * @return every factor, the pending and the removed ones too; {@link #isActive(Tables.Factor)} tells which count
   */
  public Future<List<Tables.Factor>> factors(String account) {
    return factors.children(account);
  }

  /**
   * Reads the active factors of one kind of an account.
   *
   * @param account the name of the account
   * @param kind the kind, such as {@code PASSWORD}
   * @return the factors, by name
   */
  public Future<List<Tables.Factor>> activeFactors(String account, Tables.Factor.Kind kind) {
    return factors(account).map(rows -> rows.stream().filter(row -> row.getKind() == kind && isActive(row)).toList());
  }

  /**
   * Finds the factor of a name.
   *
   * @param name the name, such as {@code factors/0b0d...}
   * @return the factor, or a failure with {@code NOT_FOUND}
   */
  public Future<Tables.Factor> factor(String name) {
    return factors.findByName(name).map(found -> found.orElseThrow(() -> new PyriteException(GrpcStatus.NOT_FOUND,
        "The factor " + name + " does not exist.")));
  }

  /**
   * Enrols a password as pending, hashing it off the event loop.
   *
   * @param account the name of the account
   * @param password the password, as the person typed it
   * @return the factor, or a failure with {@code INVALID_ARGUMENT} for a password of fewer than 8 or more than 128
   * characters
   */
  public Future<Tables.Factor> enrolPassword(String account, String password) {
    return vertx.executeBlocking(() -> PasswordHash.of(password, options.getArgon2Options()))
        .compose(hash -> enrol(account, UUID.randomUUID().toString(), Tables.Factor.newBuilder()
            .setKind(Tables.Factor.Kind.PASSWORD)
            .setSecret(ByteString.copyFrom(hash.encoded(), StandardCharsets.UTF_8))));
  }

  /**
   * Enrols a new random TOTP secret as pending, encrypted with its name as the associated data.
   *
   * @param account the name of the account
   * @return the factor, whose {@link #totpUri(Tables.Factor)} the authenticator app reads
   */
  public Future<Tables.Factor> enrolTotp(String account) {
    String id = UUID.randomUUID().toString();
    return keys.encrypt(options.getEncryptionKey(), TotpSecret.generate().bytes(),
        (FACTORS + id).getBytes(StandardCharsets.UTF_8))
        .compose(sealed -> enrol(account, id, Tables.Factor.newBuilder().setKind(Tables.Factor.Kind.TOTP)
            .setSecret(sealed.getCiphertext()).setCounter(-1)));
  }

  /**
   * Enrols a passkey as pending, from a registration that {@link WebAuthn#register(String, byte[])} verified.
   *
   * @param account the name of the account
   * @param registration the public key, the id, the signature counter and the user verification of the passkey
   * @return the factor
   */
  public Future<Tables.Factor> enrolPasskey(String account, WebAuthn.Registration registration) {
    return enrol(account, UUID.randomUUID().toString(), Tables.Factor.newBuilder()
        .setKind(Tables.Factor.Kind.PASSKEY).setSecret(ByteString.copyFrom(registration.credential()))
        .setCredentialId(registration.credentialId()).setCounter(registration.counter())
        .setUserVerified(registration.userVerified()));
  }

  /**
   * Enrols an account at a provider as a pending factor of an account, and links it, so that no other account links it.
   *
   * @param account the name of the account
   * @param provider the account at the provider, which a sign-in at the provider proved
   * @return the pending factor, or a failure with {@code ALREADY_EXISTS} when an account links the account at the
   * provider already, pending or active
   */
  public Future<Tables.Factor> enrolProvider(String account, ProviderAccount provider) {
    return enrol(account, UUID.randomUUID().toString(), Tables.Factor.newBuilder().setKind(Tables.Factor.Kind.PROVIDER)
        .setCredentialId(provider.getProvider() + ":" + provider.getId()).setSecret(provider.toByteString()))
        .compose(row -> links.insert(Tables.ProviderLink.newBuilder().setFactor(row.getName())
            .setProvider(provider.getProvider()).setAccountId(provider.getId()).build())
            .map(row)
            .recover(failure -> factors.remove(row).transform(ignored -> Future.failedFuture(
                failure instanceof DuplicateKeyException
                    ? new PyriteException(GrpcStatus.ALREADY_EXISTS, "The " + provider.getProvider() + " account "
                        + provider.getId() + " is linked to an account already.")
                    : failure))));
  }

  /**
   * Creates ten new recovery codes, as a pending factor that holds the MAC of each code.
   *
   * @param account the name of the account
   * @return the codes, which Pyrite gives out once, and the name of the factor
   */
  public Future<RecoveryCodes> createRecoveryCodes(String account) {
    List<String> codes = new ArrayList<>();
    for (int index = 0; index < RECOVERY_CODES; index++) {
      byte[] code = new byte[RECOVERY_CODE_BYTES];
      RANDOM.nextBytes(code);
      codes.add(Base64.getUrlEncoder().withoutPadding().encodeToString(code));
    }
    JsonArray macs = new JsonArray();
    Future<Void> signing = Future.succeededFuture();
    for (String code : codes) {
      signing = signing.compose(ignored -> keys.sign(options.getMacKey(), recoveryData(code)).map(mac -> {
        macs.add(Base64.getEncoder().encodeToString(mac.getSignature().toByteArray()));
        return null;
      }));
    }
    return signing.compose(ignored -> enrol(account, UUID.randomUUID().toString(), Tables.Factor.newBuilder()
        .setKind(Tables.Factor.Kind.RECOVERY_CODES).setSecret(ByteString.copyFromUtf8(macs.encode()))))
        .map(row -> RecoveryCodes.newBuilder().addAllCodes(codes).setFactor(row.getName()).build());
  }

  /**
   * Finds the active factor that links an account at a provider.
   *
   * @param provider the account at the provider
   * @return the factor, or nothing when no account links it, or the link is still pending
   */
  public Future<Optional<Tables.Factor>> linked(ProviderAccount provider) {
    return links.find(provider.getProvider(), provider.getId()).compose(link -> link.isEmpty()
        ? Future.succeededFuture(Optional.<Tables.Factor> empty())
        : factor(link.get().getFactor()).map(found -> Optional.of(found).filter(FactorService::isActive)));
  }

  /**
   * Activates a pending factor, and removes the active factors of its kind that it replaces: a password, a TOTP secret
   * or a set of recovery codes replaces the one before, and a passkey or a provider replaces none. The factors of the
   * account are locked while it runs, in one transaction, so that a failure removes nothing and two activations at once
   * leave one active factor of the kind.
   *
   * @param name the name of the factor
   * @return the active factor, or a failure with {@code NOT_FOUND}, or with {@code ABORTED} when another change came
   * first
   */
  public Future<Tables.Factor> activate(String name) {
    return factor(name).compose(read -> factors.database().transaction(tx -> factors.lockLive(read.getParent())
        .compose(live -> {
          Tables.Factor factor = live.stream().filter(row -> row.getName().equals(name)).findFirst().orElse(read);
          Future<Void> removing = Future.succeededFuture();
          if (replacesActive(factor.getKind())) {
            for (Tables.Factor other : live) {
              if (isActive(other) && other.getKind() == factor.getKind() && !other.getName().equals(name)) {
                removing = removing.compose(ignored -> remove(other).mapEmpty());
              }
            }
          }
          return removing.compose(ignored -> factors.update(factor.toBuilder().setState(Tables.Factor.State.ACTIVE)
              .clearPurgeTime().build(), factor.getUid(), factor.getRevision(), name));
        })));
  }

  /**
   * Removes an active factor that its account asks to remove; its row stays until a purge. The account keeps a factor
   * that signs in on its own: the last active password, passkey or linked provider account is not removed. The factors
   * of the account are locked while the removal runs, so two removals at once cannot both take the last two.
   *
   * @param name the name of the factor
   * @return a future that completes once it is removed, or a failure with {@code NOT_FOUND} when it does not exist or
   * is not active, or with {@code FAILED_PRECONDITION} when it is the last factor of the account that signs in on its
   * own
   */
  public Future<Void> delete(String name) {
    return factor(name).compose(read -> factors.database().transaction(tx -> factors.lockLive(read.getParent())
        .compose(live -> {
          Optional<Tables.Factor> factor = live.stream().filter(row -> row.getName().equals(name)).findFirst()
              .filter(FactorService::isActive);
          if (factor.isEmpty()) {
            return Future.failedFuture(new PyriteException(GrpcStatus.NOT_FOUND, "The factor " + name
                + " is removed."));
          }
          boolean other = live.stream().anyMatch(row -> isActive(row) && signsInAlone(row.getKind())
              && !row.getName().equals(name));
          if (signsInAlone(factor.get().getKind()) && !other) {
            return Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The factor " + name
                + " is the last that signs " + read.getParent() + " in on its own; add another first."));
          }
          return remove(factor.get()).mapEmpty();
        })));
  }

  /**
   * Deletes the rows of factors whose purge time passed: those removed or replaced, and those pending for longer than
   * the lifetime of exchanges.
   *
   * @return the number of rows
   */
  public Future<Long> purge() {
    return factors.purge();
  }

  /**
   * Verifies a password, hashing it off the event loop with the parameters of the stored hash.
   *
   * @return {@link Proof#KNOWLEDGE} when the password is the factor's, or nothing
   */
  public Future<Set<Proof>> verifyPassword(Tables.Factor factor, String password) {
    PasswordHash hash = new PasswordHash(factor.getSecret().toString(StandardCharsets.UTF_8));
    return vertx.executeBlocking(() -> hash.matches(password)).map(right -> right ? Set.of(Proof.KNOWLEDGE)
        : Set.of());
  }

  /**
   * Verifies a TOTP code: that of the current time step, or of the step before or after it, and of a step after the
   * last one that matched. A code that matches becomes the last one, so it works once. The time step comes from the
   * system time of Java, not from the clock of the database.
   *
   * @return {@link Proof#POSSESSION} when the code matches, or nothing
   */
  public Future<Set<Proof>> verifyTotp(Tables.Factor factor, String code) {
    return secret(factor).compose(secret -> {
      OptionalLong step = secret.match(code, Instant.now(), factor.getCounter());
      return step.isEmpty() ? Future.succeededFuture(Set.of())
          : change(factor.toBuilder().setCounter(step.getAsLong())).map(Set.of(Proof.POSSESSION));
    });
  }

  /**
   * Verifies a recovery code, with spaces around it or not: if one of the codes that are left is this one, the factor
   * no longer holds it, so each code works once.
   *
   * @return {@link Proof#POSSESSION} when the code was one that was left, or nothing
   */
  public Future<Set<Proof>> verifyRecoveryCode(Tables.Factor factor, String code) {
    JsonArray macs = new JsonArray(factor.getSecret().toString(StandardCharsets.UTF_8));
    Future<Integer> match = Future.succeededFuture(-1);
    for (int index = 0; index < macs.size(); index++) {
      int at = index;
      match = match.compose(found -> found >= 0 ? Future.succeededFuture(found)
          : keys.verify(options.getMacKey(), recoveryData(code), Base64.getDecoder().decode(macs.getString(at)), "")
              .map(verdict -> verdict.hasVerified() ? at : -1));
    }
    return match.compose(at -> {
      if (at < 0) {
        return Future.succeededFuture(Set.<Proof> of());
      }
      macs.remove(at.intValue());
      return change(factor.toBuilder().setSecret(ByteString.copyFromUtf8(macs.encode()))).map(Set.of(Proof.POSSESSION));
    });
  }

  /**
   * Verifies a WebAuthn assertion of a passkey, the JSON of the credential that {@code navigator.credentials.get} gave,
   * against the nonce of the stage, which the request options held as their challenge. A right assertion keeps its new
   * signature counter.
   *
   * @return {@link Proof#POSSESSION}, and {@link Proof#INHERENCE} too when the authenticator verified the user; nothing
   * for an assertion of another passkey, or one that does not verify
   */
  public Future<Set<Proof>> verifyPasskey(Tables.Factor factor, String assertion, byte[] nonce) {
    if (!webAuthn.credential(assertion).filter(factor.getCredentialId()::equals).isPresent()) {
      return Future.succeededFuture(Set.of());
    }
    Optional<WebAuthn.Assertion> verdict = webAuthn.verify(assertion, nonce, factor.getSecret().toByteArray(),
        factor.getCredentialId(), factor.getCounter(), factor.getUserVerified());
    if (verdict.isEmpty()) {
      return Future.succeededFuture(Set.of());
    }
    return change(factor.toBuilder().setCounter(verdict.get().counter())).map(saved -> verdict.get().userVerified()
        ? Set.of(Proof.POSSESSION, Proof.INHERENCE)
        : Set.of(Proof.POSSESSION));
  }

  /**
   * Verifies an account at a provider, which a sign-in at the provider proved.
   *
   * @return {@link Proof#FEDERATION} when it is the linked account, or nothing
   */
  public Future<Set<Proof>> verifyProvider(Tables.Factor factor, ProviderAccount account) {
    return Future.succeededFuture(account.getProvider().equals(provider(factor))
        && account.getId().equals(factor.getCredentialId().substring(factor.getCredentialId().indexOf(':') + 1))
            ? Set.of(Proof.FEDERATION)
            : Set.of());
  }

  /**
   * Hashes a password that no account has, so that a sign-in to an account without a password takes as long as one
   * with a password, and tells nothing of which accounts have one.
   *
   * @return a future that completes once the hash is made
   */
  public Future<Void> hashDecoy() {
    return vertx.executeBlocking(() -> PasswordHash.of(DECOY, options.getArgon2Options())).mapEmpty();
  }

  /**
   * Gives the otpauth URI of a TOTP factor, which an authenticator app reads from a QR code.
   *
   * @return the URI, labelled with the account, with the issuer of the options, SHA-1, six digits and a period of 30
   * seconds
   */
  public Future<String> totpUri(Tables.Factor factor) {
    return secret(factor).map(secret -> secret.uri(options.getIssuer(), factor.getParent()));
  }

  /**
   * Gives the WebAuthn relying party of the options, which makes the options of passkeys and checks their answers.
   *
   * @return the relying party
   */
  public WebAuthn webAuthn() {
    return webAuthn;
  }

  /**
   * Gives a factor as the authentication service gives it out, without its secret.
   *
   * @return the factor, with its name, its account, its kind, and the provider of a provider factor
   */
  public static dev.horizon.pyrite.authentication.v1.Factor message(Tables.Factor row) {
    return dev.horizon.pyrite.authentication.v1.Factor.newBuilder().setName(row.getName()).setAccount(row.getParent())
        .setKind(dev.horizon.pyrite.authentication.v1.Factor.Kind.valueOf(row.getKind().name()))
        .setProvider(row.getKind() == Tables.Factor.Kind.PROVIDER ? provider(row) : "")
        .build();
  }

  /**
   * Tells whether a factor counts in a sign-in: an exchange activated it, and it is not removed.
   */
  public static boolean isActive(Tables.Factor row) {
    return row.getState() == Tables.Factor.State.ACTIVE && !row.hasDeleteTime();
  }

  /**
   * Tells whether a factor of a kind signs in on its own, as a password, a passkey or a linked provider account do; a
   * TOTP secret and recovery codes are second factors.
   */
  public static boolean signsInAlone(Tables.Factor.Kind kind) {
    return kind == Tables.Factor.Kind.PASSWORD || kind == Tables.Factor.Kind.PASSKEY
        || kind == Tables.Factor.Kind.PROVIDER;
  }

  /**
   * Tells whether an account has one active factor of a kind, which a new one replaces when it is activated.
   */
  private static boolean replacesActive(Tables.Factor.Kind kind) {
    return kind == Tables.Factor.Kind.PASSWORD || kind == Tables.Factor.Kind.TOTP
        || kind == Tables.Factor.Kind.RECOVERY_CODES;
  }

  private static String provider(Tables.Factor row) {
    return row.getCredentialId().substring(0, row.getCredentialId().indexOf(':'));
  }

  /**
   * Gives the data whose MAC a recovery code factor holds for a code.
   */
  private static byte[] recoveryData(String code) {
    return ("recovery\0" + code.strip()).getBytes(StandardCharsets.UTF_8);
  }

  private Future<TotpSecret> secret(Tables.Factor factor) {
    return keys.decrypt(options.getEncryptionKey(), factor.getSecret().toByteArray(),
        factor.getName().getBytes(StandardCharsets.UTF_8))
        .map(plain -> new TotpSecret(plain.getPlaintext().toByteArray()));
  }

  private Future<Tables.Factor> remove(Tables.Factor factor) {
    Future<Void> unlinking = factor.getKind() == Tables.Factor.Kind.PROVIDER ? links.unlink(factor.getName())
        : Future.succeededFuture();
    return unlinking.compose(ignored -> factors.remove(factor));
  }

  private Future<Tables.Factor> change(Tables.Factor.Builder changed) {
    return factors.update(changed.build(), changed.getUid(), changed.getRevision(), changed.getName());
  }

  private Future<Tables.Factor> enrol(String account, String id, Tables.Factor.Builder row) {
    return factors.insert(row.setName(FACTORS + id).setParent(account).setState(Tables.Factor.State.PENDING)
        .build(), Map.of("purge_time", add(now(), options.getExchangeLifetime())));
  }
}
