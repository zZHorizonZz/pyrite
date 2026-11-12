package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.authentication.v1.Session;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the last read gave the last session, of an account, with the expiry of the last principal.
 */
public record ExpectSessionRead(String account) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    Session read = environment.get(Value.SESSION_READ);
    assertThat(read.getName()).as("name").isEqualTo(environment.get(Value.SESSION));
    assertThat(read.getAccount()).as("account").isEqualTo(account);
    assertThat(read.getExpireTime()).as("expiry").isEqualTo(environment.get(Value.PRINCIPAL).getExpireTime());
  }
}
