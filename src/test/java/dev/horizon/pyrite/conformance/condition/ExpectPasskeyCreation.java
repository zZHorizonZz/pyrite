package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import io.vertx.core.json.JsonObject;

/**
 * Checks that the last exchange gives creation options of a relying party, with a challenge, which exclude a number of
 * passkeys.
 */
public record ExpectPasskeyCreation(String relyingParty, int excluded) implements Condition {

  private static final int CHALLENGE_BYTES = 32;

  @Override
  public void evaluate(Environment environment) throws Exception {
    JsonObject options = PasskeyOptions.of(environment.get(Value.EXCHANGE));
    assertThat(options.getJsonObject("rp").getString("id")).as("relying party").isEqualTo(relyingParty);
    assertThat(PasskeyOptions.decode(options.getString("challenge"))).as("challenge").hasSize(CHALLENGE_BYTES);
    assertThat(options.getJsonArray("excludeCredentials")).as("excluded passkeys").hasSize(excluded);
  }
}
