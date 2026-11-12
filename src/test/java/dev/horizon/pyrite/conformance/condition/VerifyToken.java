package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.crypto.v1.Expected;
import dev.horizon.pyrite.crypto.v1.VerifyRequest;

/**
 * Verifies the last token with a key, as a subject, expecting an access token of an issuer for an audience, with a
 * leeway of a number of seconds, or none named for a negative number. Leaves the verdict.
 */
public record VerifyToken(String subject, String key, String issuer, String audience, long leeway)
    implements Condition {

  private static final long LEEWAY = 30;

  /**
   * Verifies an access token of Acme's server for its API, with a leeway of thirty seconds.
   */
  public VerifyToken(String subject, String key) {
    this(subject, key, SignToken.ISSUER, SignToken.AUDIENCE, LEEWAY);
  }

  @Override
  public void evaluate(Environment environment) throws Exception {
    Expected.Builder expected = Expected.newBuilder().setType("at+jwt").setIssuer(issuer).setAudience(audience);
    if (leeway >= 0) {
      expected.setLeeway(com.google.protobuf.Duration.newBuilder().setSeconds(leeway));
    }
    environment.put(Value.VERIFICATION, environment.pyrite().cryptography.verify(VerifyRequest.newBuilder()
        .setSubject(subject).setKey(key).setToken(environment.get(Value.TOKEN)).setExpected(expected).build())
        .await(TIMEOUT));
  }
}
