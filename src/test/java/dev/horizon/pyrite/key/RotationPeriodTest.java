package dev.horizon.pyrite.key;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.PyriteException;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RotationPeriodTest {

  @Test
  @DisplayName("zero and whole seconds from a minute to 50 years are periods")
  void inRange() {
    for (Duration period : new Duration[] { Duration.ZERO, Duration.ofMinutes(1), Duration.ofDays(90),
        Duration.ofDays(18_262) }) {
      assertThat(KeyService.checkPeriod(period).succeeded()).as(period.toString()).isTrue();
    }
  }

  @Test
  @DisplayName("a negative, fractional, too short or too long period is invalid")
  void outOfRange() {
    for (Duration period : new Duration[] { Duration.ofSeconds(-60), Duration.ofMillis(500),
        Duration.ofSeconds(90, 1), Duration.ofSeconds(59), Duration.ofDays(18_263) }) {
      assertThat(KeyService.checkPeriod(period).cause()).as(period.toString()).isInstanceOfSatisfying(
          PyriteException.class, failure -> assertThat(failure.status()).isEqualTo(GrpcStatus.INVALID_ARGUMENT));
    }
  }
}
