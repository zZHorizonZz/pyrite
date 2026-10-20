package dev.horizon.pyrite.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import dev.horizon.aquery.aip158.Page;
import dev.horizon.aquery.aip158.PageRequest;
import dev.horizon.pyrite.Algorithm;
import dev.horizon.pyrite.Encryption;
import dev.horizon.pyrite.EncryptionKey;
import dev.horizon.pyrite.Expected;
import dev.horizon.pyrite.Export;
import dev.horizon.pyrite.Key;
import dev.horizon.pyrite.Keyring;
import dev.horizon.pyrite.Mac;
import dev.horizon.pyrite.MacKey;
import dev.horizon.pyrite.Origin;
import dev.horizon.pyrite.Rejection;
import dev.horizon.pyrite.Rotation;
import dev.horizon.pyrite.Signature;
import dev.horizon.pyrite.Signing;
import dev.horizon.pyrite.SigningKey;
import dev.horizon.pyrite.State;
import dev.horizon.pyrite.Validity;
import dev.horizon.pyrite.Verification;
import dev.horizon.pyrite.Version;
import dev.horizon.pyrite.common.AbortedException;
import dev.horizon.pyrite.common.AlreadyExistsException;
import dev.horizon.pyrite.common.FailedPreconditionException;
import dev.horizon.pyrite.common.InvalidArgumentException;
import dev.horizon.pyrite.common.NotFoundException;
import dev.horizon.pyrite.internal.ProviderKeyring;
import dev.horizon.pyrite.internal.Scheme;
import dev.horizon.pyrite.spi.Provider;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.EllipticCurve;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public abstract class KeyringContract {

  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
  private static final String ISSUER = "https://app.example";
  private static final String AUDIENCE = "app.example";
  private static final Expected SESSION = new Expected("JWT", ISSUER, AUDIENCE);

  protected Vertx vertx;
  protected MovingClock clock;
  protected Keyring keyring;

  @BeforeEach
  void start() throws Exception {
    vertx = Vertx.vertx();
    clock = new MovingClock(START);
    keyring = keyring(provider(vertx, clock));
  }

  protected abstract Provider provider(Vertx vertx, Clock clock) throws Exception;

  protected Keyring keyring(Provider provider) {
    return new ProviderKeyring(provider, clock);
  }

  @AfterEach
  void stop() throws Exception {
    vertx.close().await(10, TimeUnit.SECONDS);
  }

  @Test
  void keyringIsFoundThroughTheServiceLoader() throws Exception {
    assertThat(Keyring.create(vertx, provider(vertx, clock))).isInstanceOf(ProviderKeyring.class);
  }

  @Test
  void ciphertextDecryptsUnderItsOwnContextOnly() throws Exception {
    EncryptionKey seeds = keyring.create("totp-seeds", new Encryption(), Rotation.never(), Validity.unlimited()).await(10,
        TimeUnit.SECONDS);
    byte[] sealed = seeds.encrypt(bytes("JBSWY3DPEHPK3PXP"), "accounts/42").await(10, TimeUnit.SECONDS);

    assertThat(seeds.decrypt(sealed, "accounts/42").await(10, TimeUnit.SECONDS)).isEqualTo(bytes("JBSWY3DPEHPK3PXP"));
    assertThatThrownBy(() -> seeds.decrypt(sealed, "accounts/43").await(10, TimeUnit.SECONDS))
        .isInstanceOfSatisfying(FailedPreconditionException.class,
            refused -> assertThat(refused.reason()).isEqualTo("UNREADABLE"));
  }

  @Test
  void ciphertextOfOneKeyDoesNotDecryptUnderAnother() throws Exception {
    EncryptionKey seeds = keyring.create("totp-seeds", new Encryption(), Rotation.never(), Validity.unlimited()).await(10,
        TimeUnit.SECONDS);
    EncryptionKey secrets = keyring.create("client-secrets", new Encryption(), Rotation.never(), Validity.unlimited()).await(10,
        TimeUnit.SECONDS);
    byte[] sealed = seeds.encrypt(bytes("seed"), "accounts/42").await(10, TimeUnit.SECONDS);

    assertThatThrownBy(() -> secrets.decrypt(sealed, "accounts/42").await(10, TimeUnit.SECONDS))
        .isInstanceOf(FailedPreconditionException.class);
  }

  @Test
  void rotationKeepsOlderCiphertextsReadable() throws Exception {
    EncryptionKey seeds = keyring
        .create("totp-seeds", new Encryption(), Rotation.every(Duration.ofDays(1)), Validity.unlimited()).await(10,
            TimeUnit.SECONDS);
    byte[] before = seeds.encrypt(bytes("first"), "accounts/42").await(10, TimeUnit.SECONDS);
    Version first = seeds.primary().await(10, TimeUnit.SECONDS);

    clock.move(Duration.ofDays(2));
    byte[] after = seeds.encrypt(bytes("second"), "accounts/42").await(10, TimeUnit.SECONDS);

    assertThat(seeds.primary().await(10, TimeUnit.SECONDS).id()).isNotEqualTo(first.id());
    assertThat(seeds.versions().await(10, TimeUnit.SECONDS)).hasSize(2);
    assertThat(seeds.decrypt(before, "accounts/42").await(10, TimeUnit.SECONDS)).isEqualTo(bytes("first"));
    assertThat(seeds.decrypt(after, "accounts/42").await(10, TimeUnit.SECONDS)).isEqualTo(bytes("second"));
  }

  @Test
  void disabledVersionDecryptsNothingAndPrimaryCannotBeDisabled() throws Exception {
    EncryptionKey seeds = keyring.create("totp-seeds", new Encryption(), Rotation.never(), Validity.unlimited()).await(10,
        TimeUnit.SECONDS);
    byte[] sealed = seeds.encrypt(bytes("seed"), "accounts/42").await(10, TimeUnit.SECONDS);
    Version first = seeds.primary().await(10, TimeUnit.SECONDS);

    assertThatThrownBy(() -> first.disable().await(10, TimeUnit.SECONDS)).isInstanceOfSatisfying(
        FailedPreconditionException.class,
        refused -> assertThat(refused.reason()).isEqualTo("PRIMARY"));

    seeds.rotate().await(10, TimeUnit.SECONDS);
    Version disabled = seeds.version(first.id()).await(10, TimeUnit.SECONDS).disable().await(10, TimeUnit.SECONDS);

    assertThat(disabled.state()).isInstanceOf(State.Disabled.class);
    assertThatThrownBy(() -> seeds.decrypt(sealed, "accounts/42").await(10, TimeUnit.SECONDS))
        .isInstanceOfSatisfying(FailedPreconditionException.class,
            refused -> assertThat(refused.reason()).isEqualTo("VERSION"));

    seeds.version(first.id()).await(10, TimeUnit.SECONDS).enable().await(10, TimeUnit.SECONDS);
    assertThat(seeds.decrypt(sealed, "accounts/42").await(10, TimeUnit.SECONDS)).isEqualTo(bytes("seed"));
  }

  @Test
  void fingerprintStoredBeforeRotationIsFoundAfterIt() throws Exception {
    MacKey codes = keyring.create("one-time-codes", new Mac(), Rotation.every(Duration.ofDays(1)), Validity.unlimited())
        .await(10, TimeUnit.SECONDS);
    String stored = codes.fingerprint("482913").await(10, TimeUnit.SECONDS);

    clock.move(Duration.ofDays(2));
    List<String> candidates = codes.fingerprints("482913").await(10, TimeUnit.SECONDS);

    assertThat(candidates).hasSize(2).contains(stored);
    assertThat(candidates.getFirst()).isNotEqualTo(stored);
    assertThat(codes.fingerprints("482914").await(10, TimeUnit.SECONDS)).doesNotContain(stored);
  }

  @ParameterizedTest
  @EnumSource(Algorithm.class)
  void signedTokenVerifies(Algorithm algorithm) throws Exception {
    SigningKey sessions = sessions(algorithm, Rotation.never());
    String token = sessions.jwt(claims(Duration.ofHours(1))).await(10, TimeUnit.SECONDS);

    Verification verification = sessions.verify(token, SESSION).await(10, TimeUnit.SECONDS);

    assertThat(verification).isInstanceOfSatisfying(Verification.Verified.class, verified -> {
      assertThat(verified.claims().getString("sub")).isEqualTo("accounts/42");
      assertThat(verified.version().origin()).isEqualTo(Origin.GENERATED);
    });
  }

  @ParameterizedTest
  @EnumSource(value = Algorithm.class, names = { "RS256", "ES256" })
  void publishedKeysVerifyTokensWithoutPyrite(Algorithm algorithm) throws Exception {
    SigningKey sessions = sessions(algorithm, Rotation.never());
    JWSObject token = JWSObject.parse(sessions.jwt(claims(Duration.ofHours(1))).await(10, TimeUnit.SECONDS));

    JWK jwk = JWKSet.parse(sessions.published().await(10, TimeUnit.SECONDS).encode())
        .getKeyByKeyId(token.getHeader().getKeyID());
    JWSVerifier verifier = jwk instanceof ECKey ec ? new ECDSAVerifier(ec) : new RSASSAVerifier((RSAKey) jwk);

    assertThat(token.verify(verifier)).isTrue();
    assertThat(jwk.getAlgorithm().getName()).isEqualTo(token.getHeader().getAlgorithm().getName());
  }

  @Test
  void tokenIsRejectedForEveryExpectationItMisses() throws Exception {
    SigningKey sessions = sessions(Algorithm.ES256, Rotation.never());
    String token = sessions.jwt(claims(Duration.ofHours(1))).await(10, TimeUnit.SECONDS);

    assertThat(rejection(sessions, token, new Expected("JWT", ISSUER, "admin.example"))).isEqualTo(Rejection.AUDIENCE);
    assertThat(rejection(sessions, token, new Expected("JWT", "https://other.example", AUDIENCE)))
        .isEqualTo(Rejection.ISSUER);
    assertThat(rejection(sessions, token, new Expected("at+jwt", ISSUER, AUDIENCE))).isEqualTo(Rejection.TYPE);
    assertThat(rejection(sessions, "not a token", SESSION)).isEqualTo(Rejection.MALFORMED);
  }

  @Test
  void expiredAndFutureTokensAreRejected() throws Exception {
    SigningKey sessions = sessions(Algorithm.ES256, Rotation.never());
    String expired = sessions.jwt(claims(Duration.ofHours(-1))).await(10, TimeUnit.SECONDS);
    String early = sessions.jwt(claims(Duration.ofHours(2)).put("nbf", START.plus(Duration.ofHours(1))
        .getEpochSecond())).await(10, TimeUnit.SECONDS);
    String endless = sessions.jwt(claims(Duration.ofHours(1)).put("exp", null)).await(10, TimeUnit.SECONDS);

    assertThat(rejection(sessions, expired, SESSION)).isEqualTo(Rejection.EXPIRED);
    assertThat(rejection(sessions, early, SESSION)).isEqualTo(Rejection.NOT_YET_VALID);
    assertThat(rejection(sessions, endless, SESSION)).isEqualTo(Rejection.EXPIRED);
    assertThat(sessions.verify(expired, new Expected("JWT", ISSUER, AUDIENCE, Duration.ofHours(2))).await(10, TimeUnit.SECONDS))
        .isInstanceOf(Verification.Verified.class);
  }

  @Test
  void changedClaimsBreakTheSignature() throws Exception {
    SigningKey sessions = sessions(Algorithm.ES256, Rotation.never());
    JWSObject token = JWSObject.parse(sessions.jwt(claims(Duration.ofHours(1))).await(10, TimeUnit.SECONDS));
    String forged = token.getHeader().toBase64URL() + "."
        + new Payload(claims(Duration.ofHours(1)).put("sub", "accounts/1").encode()).toBase64URL() + "."
        + token.getSignature();

    assertThat(rejection(sessions, forged, SESSION)).isEqualTo(Rejection.SIGNATURE);
  }

  @Test
  void tokenOfAnotherKeyIsRejected() throws Exception {
    SigningKey sessions = sessions(Algorithm.ES256, Rotation.never());
    SigningKey other = keyring.create("other", new Signing(Algorithm.ES256), Rotation.never(), Validity.unlimited())
        .await(10, TimeUnit.SECONDS);
    SigningKey rsa = keyring.create("rsa", new Signing(Algorithm.RS256), Rotation.never(), Validity.unlimited())
        .await(10, TimeUnit.SECONDS);
    String token = other.jwt(claims(Duration.ofHours(1))).await(10, TimeUnit.SECONDS);
    other.rotate().await(10, TimeUnit.SECONDS);
    String later = other.jwt(claims(Duration.ofHours(1))).await(10, TimeUnit.SECONDS);

    assertThat(rejection(sessions, token, SESSION)).isEqualTo(Rejection.SIGNATURE);
    assertThat(rejection(sessions, later, SESSION)).isEqualTo(Rejection.UNKNOWN_VERSION);
    assertThat(rejection(rsa, token, SESSION)).isEqualTo(Rejection.ALGORITHM);
  }

  @Test
  void rotationChangesOnlyAtTheRevisionItWasReadAt() throws Exception {
    EncryptionKey seeds = keyring.create("totp-seeds", new Encryption(), Rotation.never(), Validity.unlimited()).await(10,
        TimeUnit.SECONDS);

    Key changed = seeds.rotation(Rotation.every(Duration.ofDays(1)), seeds.revision()).await(10, TimeUnit.SECONDS);

    assertThat(changed.rotation()).isEqualTo(Rotation.every(Duration.ofDays(1)));
    assertThat(keyring.encryption("totp-seeds").await(10, TimeUnit.SECONDS).rotation())
        .isEqualTo(Rotation.every(Duration.ofDays(1)));
    assertThatThrownBy(() -> seeds.rotation(Rotation.never(), seeds.revision()).await(10, TimeUnit.SECONDS))
        .isInstanceOf(AbortedException.class);
  }

  @Test
  void versionsAreNumberedFromOneInTheirKey() throws Exception {
    EncryptionKey seeds = keyring.create("totp-seeds", new Encryption(), Rotation.never(), Validity.unlimited()).await(10,
        TimeUnit.SECONDS);
    seeds.rotate().await(10, TimeUnit.SECONDS);
    seeds.rotate().await(10, TimeUnit.SECONDS);

    assertThat(seeds.versions().await(10, TimeUnit.SECONDS)).extracting(Version::id).containsExactly("3", "2", "1");
    assertThat(seeds.primary().await(10, TimeUnit.SECONDS).id()).isEqualTo("3");
  }

  @Test
  void aNewVersionIsPendingFirstAndAnOldOneExpiresAtTheEndOfItsLifetime() throws Exception {
    SigningKey sessions = keyring.create("sessions", new Signing(Algorithm.ES256), Rotation.every(Duration.ofDays(30)),
        Validity.of(Duration.ofDays(38)).pending(Duration.ofDays(1))).await(10, TimeUnit.SECONDS);
    String old = sessions.jwt(claims(Duration.ofDays(60))).await(10, TimeUnit.SECONDS);
    String first = kid(old);
    assertThat(sessions.published().await(10, TimeUnit.SECONDS).getJsonArray("keys")).hasSize(1);
    assertThat(sessions.version(first).await(10, TimeUnit.SECONDS).expires())
        .contains(START.plus(Duration.ofDays(38)));

    clock.move(Duration.ofDays(29).plusHours(1));
    assertThat(sessions.published().await(10, TimeUnit.SECONDS).getJsonArray("keys")).hasSize(2);
    assertThat(kid(sessions.jwt(claims(Duration.ofHours(1))).await(10, TimeUnit.SECONDS))).isEqualTo(first);

    clock.move(Duration.ofDays(1));
    assertThat(kid(sessions.jwt(claims(Duration.ofHours(1))).await(10, TimeUnit.SECONDS))).isNotEqualTo(first);
    assertThat(sessions.verify(old, SESSION).await(10, TimeUnit.SECONDS)).isInstanceOf(Verification.Verified.class);

    clock.move(Duration.ofDays(8));
    assertThat(rejection(sessions, old, SESSION)).isEqualTo(Rejection.EXPIRED_VERSION);
    assertThat(sessions.published().await(10, TimeUnit.SECONDS).getJsonArray("keys")).hasSize(1);
    assertThat(sessions.version(first).await(10, TimeUnit.SECONDS).state()).isInstanceOf(State.Expired.class);
  }

  @Test
  void aKeyThatDoesNotRotateHasNoPrimaryOnceItExpiresUntilItIsRotated() throws Exception {
    SigningKey machines = keyring.create("machines", new Signing(Algorithm.ES256), Rotation.never(),
        Validity.of(Duration.ofDays(90))).await(10, TimeUnit.SECONDS);
    Version first = machines.primary().await(10, TimeUnit.SECONDS);
    Export<Version> export = machines.export().await(10, TimeUnit.SECONDS);
    assertThat(export.version().expires()).contains(START.plus(Duration.ofDays(90)));

    clock.move(Duration.ofDays(91));

    assertThatThrownBy(() -> machines.jwt(claims(Duration.ofHours(1))).await(10, TimeUnit.SECONDS))
        .isInstanceOfSatisfying(FailedPreconditionException.class,
            refused -> assertThat(refused.reason()).isEqualTo("PRIMARY"));
    assertThat(machines.version(export.version().id()).await(10, TimeUnit.SECONDS).state())
        .isInstanceOf(State.Expired.class);
    assertThatThrownBy(() -> machines.version(first.id()).await(10, TimeUnit.SECONDS).enable()
        .await(10, TimeUnit.SECONDS))
        .isInstanceOfSatisfying(FailedPreconditionException.class,
            refused -> assertThat(refused.reason()).isEqualTo("VERSION"));

    Version second = machines.rotate().await(10, TimeUnit.SECONDS);
    assertThat(second.state()).isInstanceOf(State.Primary.class);
    assertThat(machines.verify(machines.jwt(claims(Duration.ofDays(92))).await(10, TimeUnit.SECONDS), SESSION)
        .await(10, TimeUnit.SECONDS)).isInstanceOf(Verification.Verified.class);
  }

  @Test
  void anExpiredVersionDecryptsNothing() throws Exception {
    EncryptionKey seeds = keyring.create("totp-seeds", new Encryption(), Rotation.every(Duration.ofDays(30)),
        Validity.of(Duration.ofDays(60))).await(10, TimeUnit.SECONDS);
    byte[] sealed = seeds.encrypt(bytes("seed"), "accounts/42").await(10, TimeUnit.SECONDS);

    clock.move(Duration.ofDays(45));
    assertThat(seeds.decrypt(sealed, "accounts/42").await(10, TimeUnit.SECONDS)).isEqualTo(bytes("seed"));

    clock.move(Duration.ofDays(16));
    assertThatThrownBy(() -> seeds.decrypt(sealed, "accounts/42").await(10, TimeUnit.SECONDS))
        .isInstanceOfSatisfying(FailedPreconditionException.class,
            refused -> assertThat(refused.reason()).isEqualTo("VERSION"));
  }

  @Test
  void aValidityThatLeavesAKeyWithoutAPrimaryIsRefused() throws Exception {
    assertThatThrownBy(() -> keyring.create("sessions", new Signing(Algorithm.ES256),
        Rotation.every(Duration.ofDays(30)), Validity.of(Duration.ofDays(30)).pending(Duration.ofDays(1)))
        .await(10, TimeUnit.SECONDS))
        .isInstanceOf(InvalidArgumentException.class);
    assertThatThrownBy(() -> keyring.create("machines", new Signing(Algorithm.ES256), Rotation.never(),
        Validity.of(Duration.ofHours(1)).pending(Duration.ofHours(1))).await(10, TimeUnit.SECONDS))
        .isInstanceOf(InvalidArgumentException.class);
    assertThatThrownBy(() -> keyring.create("seeds", new Encryption(), Rotation.every(Duration.ZERO),
        Validity.unlimited()).await(10, TimeUnit.SECONDS))
        .isInstanceOf(InvalidArgumentException.class);

    EncryptionKey seeds = keyring.create("seeds", new Encryption(), Rotation.never(), Validity.of(Duration.ofDays(10)))
        .await(10, TimeUnit.SECONDS);
    assertThatThrownBy(() -> seeds.rotation(Rotation.every(Duration.ofDays(30)), seeds.revision())
        .await(10, TimeUnit.SECONDS))
        .isInstanceOf(InvalidArgumentException.class);
  }

  @Test
  void aChangedValidityHoldsForTheVersionsMadeAfterIt() throws Exception {
    EncryptionKey seeds = keyring.create("seeds", new Encryption(), Rotation.never(), Validity.unlimited())
        .await(10, TimeUnit.SECONDS);
    Version first = seeds.primary().await(10, TimeUnit.SECONDS);

    Key changed = seeds.validity(Validity.of(Duration.ofDays(10)), seeds.revision()).await(10, TimeUnit.SECONDS);
    Version second = changed.rotate().await(10, TimeUnit.SECONDS);

    assertThat(changed.validity()).isEqualTo(Validity.of(Duration.ofDays(10)));
    assertThat(seeds.version(first.id()).await(10, TimeUnit.SECONDS).expires()).isEmpty();
    assertThat(second.expires()).contains(START.plus(Duration.ofDays(10)));
  }

  @Test
  void bytesAreSignedAndVerified() throws Exception {
    SigningKey blobs = sessions(Algorithm.RS256, Rotation.never());
    Signature signature = blobs.sign(bytes("payload")).await(10, TimeUnit.SECONDS);

    assertThat(blobs.verify(bytes("payload"), signature).await(10, TimeUnit.SECONDS)).isInstanceOf(Verification.Verified.class);
    assertThat(blobs.verify(bytes("changed"), signature).await(10, TimeUnit.SECONDS))
        .isInstanceOfSatisfying(Verification.Rejected.class,
            rejected -> assertThat(rejected.reason()).isEqualTo(Rejection.SIGNATURE));
  }

  @Test
  void exportedVersionVerifiesWhatItsHolderSigns() throws Exception {
    SigningKey machine = keyring.create("machines", new Signing(Algorithm.ES256), Rotation.never(),
        Validity.unlimited()).await(10, TimeUnit.SECONDS);
    Export<Version> export = machine.export().await(10, TimeUnit.SECONDS);
    ECPrivateKey privateKey = (ECPrivateKey) Scheme.ES256.privateKey(export.privateKey());

    JWSObject assertion = new JWSObject(new JWSHeader.Builder(JWSAlgorithm.ES256)
        .type(JOSEObjectType.JWT)
        .keyID(export.version().id())
        .build(), new Payload(claims(Duration.ofMinutes(5)).encode()));
    assertion.sign(new ECDSASigner(privateKey));

    assertThat(export.version().origin()).isEqualTo(Origin.EXPORTED);
    assertThat(machine.primary().await(10, TimeUnit.SECONDS).id()).isNotEqualTo(export.version().id());
    assertThat(machine.verify(assertion.serialize(), SESSION).await(10, TimeUnit.SECONDS))
        .isInstanceOfSatisfying(Verification.Verified.class,
            verified -> assertThat(verified.version().id()).isEqualTo(export.version().id()));
  }

  @Test
  void uploadedKeyIsCheckedAgainstTheAlgorithm() throws Exception {
    SigningKey machine = keyring.create("machines", new Signing(Algorithm.ES256), Rotation.never(),
        Validity.unlimited()).await(10, TimeUnit.SECONDS);
    KeyPairGenerator ec = KeyPairGenerator.getInstance("EC");
    ec.initialize(new ECGenParameterSpec("secp256r1"));
    KeyPair pair = ec.generateKeyPair();
    KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
    rsa.initialize(2048);

    Version uploaded = machine.upload(pair.getPublic()).await(10, TimeUnit.SECONDS);

    assertThat(uploaded.origin()).isEqualTo(Origin.UPLOADED);
    assertThatThrownBy(() -> machine.upload(rsa.generateKeyPair().getPublic()).await(10, TimeUnit.SECONDS))
        .isInstanceOf(InvalidArgumentException.class);
    assertThatThrownBy(() -> machine.upload(new Secp256k1Key()).await(10, TimeUnit.SECONDS))
        .isInstanceOf(InvalidArgumentException.class);
  }

  @Test
  void keysAreCreatedOnceAndAskedForByTheirPurpose() throws Exception {
    keyring.create("totp-seeds", new Encryption(), Rotation.never(), Validity.unlimited()).await(10, TimeUnit.SECONDS);

    assertThatThrownBy(
        () -> keyring.create("totp-seeds", new Mac(), Rotation.never(), Validity.unlimited()).await(10, TimeUnit.SECONDS))
        .isInstanceOf(AlreadyExistsException.class);
    assertThatThrownBy(
        () -> keyring.create("totp seeds", new Mac(), Rotation.never(), Validity.unlimited()).await(10, TimeUnit.SECONDS))
        .isInstanceOf(InvalidArgumentException.class);
    assertThatThrownBy(() -> keyring.mac("totp-seeds").await(10, TimeUnit.SECONDS))
        .isInstanceOfSatisfying(FailedPreconditionException.class,
            refused -> assertThat(refused.reason()).isEqualTo("PURPOSE"));
    assertThatThrownBy(() -> keyring.signing("sessions").await(10, TimeUnit.SECONDS)).isInstanceOf(NotFoundException.class);
    assertThat(keyring.encryption("totp-seeds").await(10, TimeUnit.SECONDS).name()).isEqualTo("totp-seeds");
  }

  @Test
  void keysAreListedInPagesInTheOrderOfTheirNames() throws Exception {
    for (String name : List.of("c", "a", "b")) {
      keyring.create(name, new Mac(), Rotation.never(), Validity.unlimited()).await(10, TimeUnit.SECONDS);
    }

    Page<Key> first = keyring.keys(new PageRequest("", "", 2, "")).await(10, TimeUnit.SECONDS);
    Page<Key> second = keyring.keys(new PageRequest("", "", 2, first.nextPageToken())).await(10, TimeUnit.SECONDS);

    assertThat(first.items()).extracting(Key::name).containsExactly("a", "b");
    assertThat(second.items()).extracting(Key::name).containsExactly("c");
    assertThat(second.nextPageToken()).isEmpty();
  }

  private SigningKey sessions(Algorithm algorithm, Rotation rotation) throws Exception {
    return keyring.create("sessions", new Signing(algorithm), rotation, Validity.unlimited())
        .await(10, TimeUnit.SECONDS);
  }

  private JsonObject claims(Duration lifetime) {
    return new JsonObject()
        .put("iss", ISSUER)
        .put("aud", AUDIENCE)
        .put("sub", "accounts/42")
        .put("exp", START.plus(lifetime).getEpochSecond());
  }

  private Rejection rejection(SigningKey key, String token, Expected expected) throws Exception {
    Verification verification = key.verify(token, expected).await(10, TimeUnit.SECONDS);
    assertThat(verification).isInstanceOf(Verification.Rejected.class);
    return ((Verification.Rejected) verification).reason();
  }

  private String kid(String token) {
    try {
      return JWSObject.parse(token).getHeader().getKeyID();
    } catch (ParseException malformed) {
      throw new AssertionError(malformed);
    }
  }

  private byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private record Secp256k1Key() implements ECPublicKey {

    private static final ECParameterSpec SECP256K1 = new ECParameterSpec(
        new EllipticCurve(new ECFieldFp(new BigInteger(
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16)), BigInteger.ZERO,
            BigInteger.valueOf(7)),
        new ECPoint(new BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16),
            new BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16)),
        new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16), 1);

    @Override
    public ECPoint getW() {
      return SECP256K1.getGenerator();
    }

    @Override
    public ECParameterSpec getParams() {
      return SECP256K1;
    }

    @Override
    public String getAlgorithm() {
      return "EC";
    }

    @Override
    public String getFormat() {
      return "X.509";
    }

    @Override
    public byte[] getEncoded() {
      return new byte[0];
    }
  }
}
