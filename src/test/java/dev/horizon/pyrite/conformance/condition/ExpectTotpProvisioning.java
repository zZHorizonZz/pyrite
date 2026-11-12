package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.authentication.v1.TotpProvisioning;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the last flow gives an otpauth URI of an issuer, with SHA-1, six digits, thirty seconds, and a secret
 * of twenty bytes.
 */
public record ExpectTotpProvisioning(String issuer) implements Condition {

  private static final int SECRET_BYTES = 20;

  @Override
  public void evaluate(Environment environment) throws Exception {
    String uri = environment.get(Value.FLOW).getChallenges(0).getParameters().unpack(TotpProvisioning.class)
        .getUri();
    assertThat(uri).startsWith("otpauth://totp/" + issuer + ":").contains("issuer=" + issuer, "algorithm=SHA1",
        "digits=6", "period=30");
    assertThat(KeepTotpSecret.secret(uri)).hasSize(SECRET_BYTES);
  }
}
