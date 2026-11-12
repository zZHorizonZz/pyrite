package dev.horizon.pyrite.conformance.condition;

import com.google.common.io.BaseEncoding;
import dev.horizon.pyrite.authentication.v1.TotpProvisioning;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.net.URI;

/**
 * Keeps the secret of the TOTP enrolment that the last flow started.
 */
public record KeepTotpSecret(Value<byte[]> into) implements Condition {

  /**
   * Reads the secret of an otpauth URI.
   */
  public static byte[] secret(String uri) {
    for (String parameter : URI.create(uri).getRawQuery().split("&")) {
      if (parameter.startsWith("secret=")) {
        return BaseEncoding.base32().decode(parameter.substring("secret=".length()));
      }
    }
    throw new AssertionError("The URI " + uri + " has no secret");
  }

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.put(into, secret(environment.get(Value.FLOW).getChallenges(0).getParameters()
        .unpack(TotpProvisioning.class).getUri()));
  }
}
