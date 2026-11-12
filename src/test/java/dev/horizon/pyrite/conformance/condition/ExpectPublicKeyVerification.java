package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.crypto.v1.Algorithm;
import dev.horizon.pyrite.crypto.v1.KeyVersion;
import dev.horizon.pyrite.crypto.v1.ListKeyVersionsRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * Checks that the last token names the first version of a key as its key id and the type at+jwt, and that it
 * verifies with the public key that the version publishes.
 */
public record ExpectPublicKeyVerification(String key, Algorithm algorithm) implements Condition {

  /**
   * Gives the public key that a version of a key publishes.
   */
  public static JWK publicKey(Environment environment, String key, String version) throws Exception {
    KeyVersion found = environment.pyrite().cryptography.listKeyVersions(ListKeyVersionsRequest.newBuilder()
        .setSubject(ANNE).setKey(key).build()).await(TIMEOUT).getVersionsList().stream()
        .filter(candidate -> candidate.getName().equals(version)).findFirst().orElseThrow();
    Map<String, Object> fields = new HashMap<>();
    found.getPublicKey().getFieldsMap().forEach((name, field) -> fields.put(name, field.getStringValue()));
    return JWK.parse(fields);
  }

  @Override
  public void evaluate(Environment environment) throws Exception {
    String id = key.substring(key.indexOf('/') + 1);
    SignedJWT jwt = SignedJWT.parse(environment.get(Value.TOKEN));
    assertThat(jwt.getHeader().getKeyID()).as("kid").isEqualTo(id + "-1");
    assertThat(jwt.getHeader().getType().getType()).as("typ").isEqualTo("at+jwt");
    JWK jwk = publicKey(environment, key, "keyVersions/" + id + "-1");
    assertThat(jwk.isPrivate()).as("private").isFalse();
    JWSVerifier verifier = switch (algorithm) {
      case RS256 -> new RSASSAVerifier((RSAKey) jwk);
      case ES256 -> new ECDSAVerifier((ECKey) jwk);
      default -> new Ed25519Verifier((OctetKeyPair) jwk);
    };
    assertThat(jwt.verify(verifier)).as("verified with the published key").isTrue();
  }
}
