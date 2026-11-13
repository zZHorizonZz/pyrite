package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.horizon.pyrite.authentication.v1.Principal;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.time.Duration;
import java.time.Instant;

/**
 * Checks that the last principal is of a subject and an account, with the last session, which expires after a
 * lifetime from about now.
 */
public record ExpectPrincipal(String subject, String account, Duration lifetime) implements Condition {

  private static final long CLOCK_SECONDS = 120;

  @Override
  public void evaluate(Environment environment) {
    Principal principal = environment.get(Value.PRINCIPAL);
    assertThat(principal.getSubject()).as("subject").isEqualTo(subject);
    assertThat(principal.getAccount()).as("account").isEqualTo(account);
    assertThat(principal.getSession()).as("session").isEqualTo(environment.get(Value.SESSION));
    assertThat(principal.getExpireTime().getSeconds()).as("expiry").isCloseTo(Instant.now().plus(lifetime)
        .getEpochSecond(), within(CLOCK_SECONDS));
  }
}
