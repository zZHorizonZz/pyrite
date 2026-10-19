package dev.horizon.pyrite.internal;

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
import dev.horizon.pyrite.Verification;
import dev.horizon.pyrite.Version;
import dev.horizon.pyrite.common.AbortedException;
import dev.horizon.pyrite.common.AlreadyExistsException;
import dev.horizon.pyrite.common.FailedPreconditionException;
import dev.horizon.pyrite.common.InvalidArgumentException;
import dev.horizon.pyrite.common.NotFoundException;
import dev.horizon.pyrite.memory.MemoryProvider;
import dev.horizon.pyrite.spi.Provider;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

abstract class KeyringContract {

  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
  private static final String ISSUER = "https://app.example";
  private static final String AUDIENCE = "app.example";
  private static final Expected SESSION = new Expected("JWT", ISSUER, AUDIENCE);

  protected Vertx vertx;
  protected MovingClock clock;
  protected ProviderKeyring keyring;

  @BeforeEach
  void start() throws Exception {
    vertx = Vertx.vertx();
    clock = new MovingClock(START);
    keyring = new ProviderKeyring(provider(vertx, clock), clock);
  }

  protected abstract Provider provider(Vertx vertx, Clock clock) throws Exception;

  @AfterEach
  void stop() throws Exception {
    vertx.close().await(10, TimeUnit.SECONDS);
  }

  @Test
  void keyringIsFoundThroughTheServiceLoader() throws Exception {
    assertThat(Keyring.create(vertx, new MemoryProvider(vertx))).isInstanceOf(ProviderKeyring.class);
  }

  @Test
  void ciphertextDecryptsUnderItsOwnContextOnly() throws Exception {
    EncryptionKey seeds = keyring.create("totp-seeds", new Encryption(), Rotation.never()).await(10, TimeUnit.SECONDS);
    byte[] sealed = seeds.encrypt(bytes("JBSWY3DPEHPK3PXP"), "accounts/42").await(10, TimeUnit.SECONDS);

    assertThat(seeds.decrypt(sealed, "accounts/42").await(10, TimeUnit.SECONDS)).isEqualTo(bytes("JBSWY3DPEHPK3PXP"));
    assertThatThrownBy(() -> seeds.decrypt(sealed, "accounts/43").await(10, TimeUnit.SECONDS))
        .isInstanceOfSatisfying(FailedPreconditionException.class,
            refused -> assertThat(refused.reason()).isEqualTo("UNREADABLE"));
  }

  @Test
  void ciphertextOfOneKeyDoesNotDecryptUnderAnother() throws Exception {
    EncryptionKey seeds = keyring.create("totp-seeds", new Encryption(), Rotation.never()).await(10, TimeUnit.SECONDS);
    EncryptionKey secrets = keyring.create("client-secrets", new Encryption(), Rotation.never()).await(10, TimeUnit.SECONDS);
    byte[] sealed = seeds.encrypt(bytes("seed"), "accounts/42").await(10, TimeUnit.SECONDS);

    assertThatThrownBy(() -> secrets.decrypt(sealed, "accounts/42").await(10, TimeUnit.SECONDS))
        .isInstanceOf(FailedPreconditionException.class);
  }

  @Test
  void rotationKeepsOlderCiphertextsReadable() throws Exception {
    EncryptionKey seeds = keyring.create("totp-seeds", new Encryption(), Rotation.every(Duration.ofDays(1))).await(10,
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
    EncryptionKey seeds = keyring.create("totp-seeds", new Encryption(), Rotation.never()).await(10, TimeUnit.SECONDS);
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
    MacKey codes = keyring.create("one-time-codes", new Mac(), Rotation.every(Duration.ofDays(1))).await(10, TimeUnit.SECONDS);
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
    SigningKey other = keyring.create("other", new Signing(Algorithm.ES256, Duration.ZERO, Duration.ZERO),
        Rotation.never()).await(10, TimeUnit.SECONDS);
    SigningKey rsa = keyring.create("rsa", new Signing(Algorithm.RS256, Duration.ZERO, Duration.ZERO),
        Rotation.never()).await(10, TimeUnit.SECONDS);
    String token = other.jwt(claims(Duration.ofHours(1))).await(10, TimeUnit.SECONDS);
    other.rotate().await(10, TimeUnit.SECONDS);
    String later = other.jwt(claims(Duration.ofHours(1))).await(10, TimeUnit.SECONDS);

    assertThat(rejection(sessions, token, SESSION)).isEqualTo(Rejection.SIGNATURE);
    assertThat(rejection(sessions, later, SESSION)).isEqualTo(Rejection.UNKNOWN_VERSION);
    assertThat(rejection(rsa, token, SESSION)).isEqualTo(Rejection.ALGORITHM);
  }

  @Test
  void rotationChangesOnlyAtTheRevisionItWasReadAt() throws Exception {
    EncryptionKey seeds = keyring.create("totp-seeds", new Encryption(), Rotation.never()).await(10, TimeUnit.SECONDS);

    Key changed = seeds.rotation(Rotation.every(Duration.ofDays(1)), seeds.revision()).await(10, TimeUnit.SECONDS);

    assertThat(changed.rotation()).isEqualTo(Rotation.every(Duration.ofDays(1)));
    assertThat(keyring.encryption("totp-seeds").await(10, TimeUnit.SECONDS).rotation())
        .isEqualTo(Rotation.every(Duration.ofDays(1)));
    assertThatThrownBy(() -> seeds.rotation(Rotation.never(), seeds.revision()).await(10, TimeUnit.SECONDS))
        .isInstanceOf(AbortedException.class);
  }

  @Test
  void versionsAreNumberedFromOneInTheirKey() throws Exception {
    EncryptionKey seeds = keyring.create("totp-seeds", new Encryption(), Rotation.never()).await(10, TimeUnit.SECONDS);
    seeds.rotate().await(10, TimeUnit.SECONDS);
    seeds.rotate().await(10, TimeUnit.SECONDS);

    assertThat(seeds.versions().await(10, TimeUnit.SECONDS)).extracting(Version::id).containsExactly("3", "2", "1");
    assertThat(seeds.primary().await(10, TimeUnit.SECONDS).id()).isEqualTo("3");
  }

  @Test
  void signingKeyPublishesAheadAndRetiresAfterItsTokensExpire() throws Exception {
    SigningKey sessions = keyring.create("sessions", new Signing(Algorithm.ES256, Duration.ofDays(1),
        Duration.ofDays(7)), Rotation.every(Duration.ofDays(30))).await(10, TimeUnit.SECONDS);
    String old = sessions.jwt(claims(Duration.ofDays(60))).await(10, TimeUnit.SECONDS);
    String first = kid(old);
    assertThat(sessions.published().await(10, TimeUnit.SECONDS).getJsonArray("keys")).hasSize(1);

    clock.move(Duration.ofDays(29).plusHours(1));
    assertThat(sessions.published().await(10, TimeUnit.SECONDS).getJsonArray("keys")).hasSize(2);
    assertThat(kid(sessions.jwt(claims(Duration.ofHours(1))).await(10, TimeUnit.SECONDS))).isEqualTo(first);

    clock.move(Duration.ofDays(1));
    assertThat(kid(sessions.jwt(claims(Duration.ofHours(1))).await(10, TimeUnit.SECONDS))).isNotEqualTo(first);
    assertThat(sessions.verify(old, SESSION).await(10, TimeUnit.SECONDS)).isInstanceOf(Verification.Verified.class);

    clock.move(Duration.ofDays(7));
    assertThat(rejection(sessions, old, SESSION)).isEqualTo(Rejection.DISABLED_VERSION);
    assertThat(sessions.published().await(10, TimeUnit.SECONDS).getJsonArray("keys")).hasSize(1);
    assertThat(sessions.version(first).await(10, TimeUnit.SECONDS).state()).isInstanceOf(State.Disabled.class);
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
    SigningKey machine = sessions(Algorithm.ES256, Rotation.never());
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
    assertThat(machine.certificates().await(10, TimeUnit.SECONDS)).containsKey(export.version().id());
  }

  @Test
  void uploadedKeyIsCheckedAgainstTheAlgorithm() throws Exception {
    SigningKey machine = sessions(Algorithm.ES256, Rotation.never());
    KeyPairGenerator ec = KeyPairGenerator.getInstance("EC");
    ec.initialize(new ECGenParameterSpec("secp256r1"));
    KeyPair pair = ec.generateKeyPair();
    KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
    rsa.initialize(2048);

    Version uploaded = machine.upload(pair.getPublic()).await(10, TimeUnit.SECONDS);

    assertThat(uploaded.origin()).isEqualTo(Origin.UPLOADED);
    assertThatThrownBy(() -> machine.upload(rsa.generateKeyPair().getPublic()).await(10, TimeUnit.SECONDS))
        .isInstanceOf(InvalidArgumentException.class);
  }

  @Test
  void everyGeneratedVersionHasACertificateItSigned() throws Exception {
    SigningKey sessions = sessions(Algorithm.RS256, Rotation.never());
    Map<String, String> certificates = sessions.certificates().await(10, TimeUnit.SECONDS);
    String id = sessions.primary().await(10, TimeUnit.SECONDS).id();

    X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
        .generateCertificate(new ByteArrayInputStream(certificates.get(id).getBytes(StandardCharsets.US_ASCII)));
    certificate.verify(certificate.getPublicKey());

    assertThat(certificate.getSubjectX500Principal().getName()).isEqualTo("CN=sessions");
  }

  @Test
  void keysAreCreatedOnceAndAskedForByTheirPurpose() throws Exception {
    keyring.create("totp-seeds", new Encryption(), Rotation.never()).await(10, TimeUnit.SECONDS);

    assertThatThrownBy(() -> keyring.create("totp-seeds", new Mac(), Rotation.never()).await(10, TimeUnit.SECONDS))
        .isInstanceOf(AlreadyExistsException.class);
    assertThatThrownBy(() -> keyring.create("totp seeds", new Mac(), Rotation.never()).await(10, TimeUnit.SECONDS))
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
      keyring.create(name, new Mac(), Rotation.never()).await(10, TimeUnit.SECONDS);
    }

    Page<Key> first = keyring.keys(new PageRequest("", "", 2, "")).await(10, TimeUnit.SECONDS);
    Page<Key> second = keyring.keys(new PageRequest("", "", 2, first.nextPageToken())).await(10, TimeUnit.SECONDS);

    assertThat(first.items()).extracting(Key::name).containsExactly("a", "b");
    assertThat(second.items()).extracting(Key::name).containsExactly("c");
    assertThat(second.nextPageToken()).isEmpty();
  }

  private SigningKey sessions(Algorithm algorithm, Rotation rotation) throws Exception {
    return keyring.create("sessions", new Signing(algorithm, Duration.ZERO, Duration.ofDays(1)), rotation).await(10,
        TimeUnit.SECONDS);
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
}
