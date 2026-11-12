package dev.horizon.pyrite.identity;

import static dev.horizon.basepoint.expression.Expression.add;
import static dev.horizon.basepoint.expression.Expression.now;

import com.google.protobuf.ByteString;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.AuthenticationOptions;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.key.EncryptionKey;
import dev.horizon.pyrite.key.KeyType;
import dev.horizon.pyrite.key.SigningKey;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.storage.FactorBackend;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.grpc.common.GrpcStatus;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The credentials of accounts: it enrols them as pending, finds them, and purges those that a flow never activated
 * or a newer credential replaced. Passwords are hashed with the Argon2id options of authentication, TOTP secrets are
 * encrypted with its encryption key, and recovery codes are kept as MACs of its MAC key.
 */
public final class CredentialType {

  /**
   * The first segment of the name of a credential, such as {@code factors/0b0d...}.
   */
  public static final String FACTORS = "factors/";

  private static final String DECOY = "a password that no account has";

  private final FactorBackend factors;
  private final Vertx vertx;
  private final AuthenticationOptions options;
  private final KeyType keys;
  private final WebAuthn webAuthn;

  public CredentialType(Database database, Vertx vertx, AuthenticationOptions options,
      KeyType keys) {
    this.factors = new FactorBackend(database);
    this.vertx = vertx;
    this.options = options;
    this.keys = keys;
    this.webAuthn = new WebAuthn(options.getRelyingPartyOptions());
  }

  /**
   * Reads the credentials of an account.
   *
   * @param account the name of the account
   * @return every credential, the pending and the removed ones too
   */
  public Future<List<Credential>> credentials(String account) {
    return factors.children(account).map(rows -> rows.stream().map(this::credential).toList());
  }

  /**
   * Finds the credential of a name.
   *
   * @param name the name, such as {@code factors/0b0d...}
   * @return the credential, or a failure with {@code NOT_FOUND}
   */
  public Future<Credential> credential(String name) {
    return factors.findByName(name).map(found -> credential(found.orElseThrow(() -> new PyriteException(
        GrpcStatus.NOT_FOUND, "The factor " + name + " does not exist."))));
  }

  /**
   * Enrols a password as pending, hashing it off the event loop.
   *
   * @param account the name of the account
   * @param password the password, as the person typed it
   * @return the credential, or a failure with {@code INVALID_ARGUMENT} for a password of fewer than 8 or more than 128
   * characters
   */
  public Future<PasswordCredential> enrolPassword(String account, String password) {
    return vertx.executeBlocking(() -> PasswordHash.of(password, options.getArgon2Options()))
        .compose(hash -> enrol(account, UUID.randomUUID().toString(), Tables.Factor.newBuilder()
            .setKind(Tables.Factor.Kind.PASSWORD)
            .setSecret(ByteString.copyFrom(hash.encoded(), StandardCharsets.UTF_8))))
        .map(PasswordCredential.class::cast);
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
   * Enrols a new random TOTP secret as pending, encrypted with its name as the associated data.
   *
   * @param account the name of the account
   * @return the credential, whose {@link TotpCredential#uri(String)} the authenticator app reads
   */
  public Future<TotpCredential> enrolTotp(String account) {
    String id = UUID.randomUUID().toString();
    return encryptionKey()
        .compose(key -> key.encrypt(TotpSecret.generate().bytes(), (FACTORS + id).getBytes(StandardCharsets.UTF_8)))
        .compose(sealed -> enrol(account, id, Tables.Factor.newBuilder().setKind(Tables.Factor.Kind.TOTP)
            .setSecret(sealed.getCiphertext()).setCounter(-1)))
        .map(TotpCredential.class::cast);
  }

  /**
   * Enrols a passkey as pending, from a registration that {@link WebAuthn#register(String, byte[])} verified.
   *
   * @param account the name of the account
   * @param registration the public key, the id, the signature counter and the user verification of the passkey
   * @return the credential
   */
  public Future<PasskeyCredential> enrolPasskey(String account, WebAuthn.Registration registration) {
    return enrol(account, UUID.randomUUID().toString(), Tables.Factor.newBuilder()
        .setKind(Tables.Factor.Kind.PASSKEY).setSecret(ByteString.copyFrom(registration.credential()))
        .setCredentialId(registration.credentialId()).setCounter(registration.counter())
        .setUserVerified(registration.userVerified()))
        .map(PasskeyCredential.class::cast);
  }

  /**
   * Creates ten new recovery codes, as a pending credential that holds the MAC of each code.
   *
   * @param account the name of the account
   * @return the credential with the codes, which Pyrite gives out once
   */
  public Future<RecoveryCodeSet> createRecoveryCodes(String account) {
    List<String> codes = RecoveryCodeCredential.generate();
    JsonArray macs = new JsonArray();
    return macKey().compose(key -> {
      Future<Void> signing = Future.succeededFuture();
      for (String code : codes) {
        signing = signing.compose(ignored -> key.sign(RecoveryCodeCredential.data(code)).map(mac -> {
          macs.add(Base64.getEncoder().encodeToString(mac.getSignature().toByteArray()));
          return null;
        }));
      }
      return signing;
    }).compose(ignored -> enrol(account, UUID.randomUUID().toString(), Tables.Factor.newBuilder()
        .setKind(Tables.Factor.Kind.RECOVERY_CODES).setSecret(ByteString.copyFromUtf8(macs.encode()))))
        .map(credential -> new RecoveryCodeSet((RecoveryCodeCredential) credential, codes));
  }

  /**
   * Deletes the rows of credentials whose purge time passed: those removed or replaced, and those pending for longer
   * than the lifetime of flows.
   *
   * @return the number of rows
   */
  public Future<Long> purge() {
    return factors.purge();
  }

  /**
   * Gives the WebAuthn relying party of the options, which makes the options of passkeys and checks their answers.
   *
   * @return the relying party
   */
  public WebAuthn webAuthn() {
    return webAuthn;
  }

  Vertx vertx() {
    return vertx;
  }

  String issuer() {
    return options.getIssuer();
  }

  Future<SigningKey> macKey() {
    return keys.signing(options.getMacKey());
  }

  Future<EncryptionKey> encryptionKey() {
    return keys.encryption(options.getEncryptionKey());
  }

  Future<Credential> remove(Credential credential) {
    return factors.remove(credential.row()).map(this::credential);
  }

  Future<Credential> save(Credential credential, Tables.Factor.Builder changed) {
    return factors.update(changed.build(), credential.row().getUid(), credential.row().getRevision(),
        credential.name()).map(this::credential);
  }

  private Future<Credential> enrol(String account, String id, Tables.Factor.Builder row) {
    return factors.insert(row.setName(FACTORS + id).setParent(account).setState(Tables.Factor.State.PENDING)
        .build(), Map.of("purge_time", add(now(), options.getFlowLifetime()))).map(this::credential);
  }

  private Credential credential(Tables.Factor row) {
    return switch (row.getKind()) {
      case PASSWORD -> new PasswordCredential(this, row);
      case TOTP -> new TotpCredential(this, row);
      case PASSKEY -> new PasskeyCredential(this, row);
      case RECOVERY_CODES -> new RecoveryCodeCredential(this, row);
      case KIND_UNSPECIFIED, UNRECOGNIZED -> throw new IllegalStateException("The factor " + row.getName()
          + " has no kind.");
    };
  }
}
