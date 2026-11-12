package dev.horizon.pyrite.conformance.condition;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;

/**
 * Signs a JWT of user:root with HS256, keyed with the published public key of a version of a key, and takes it as the
 * last token: a forgery that confuses the algorithm of the key.
 */
public record ForgeAlgorithmConfusion(String key, String version) implements Condition {

  private static final long EXPIRES_IN = 600;

  @Override
  public void evaluate(Environment environment) throws Exception {
    byte[] secret = MessageDigest.getInstance("SHA-256").digest(ExpectPublicKeyVerification.publicKey(environment,
        key, version).toJSONString().getBytes(StandardCharsets.UTF_8));
    SignedJWT forged = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(new JOSEObjectType("at+jwt"))
        .keyID(version.substring(version.indexOf('/') + 1)).build(),
        new JWTClaimsSet.Builder()
            .issuer(SignToken.ISSUER).audience(SignToken.AUDIENCE).subject("user:root").expirationTime(new Date(
                (Instant.now().getEpochSecond() + EXPIRES_IN) * 1000))
            .build());
    forged.sign(new MACSigner(secret));
    environment.put(Value.TOKEN, forged.serialize());
  }
}
