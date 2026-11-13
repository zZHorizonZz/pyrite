package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.PyriteFixture.status;
import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import io.vertx.grpc.common.GrpcStatus;

/**
 * Trades the refresh token that the last refresh traded once more, and checks that it is refused and that it ended the
 * session: the newest refresh token and access token no longer work either.
 */
public record ExpectSpentRefreshRefusal() implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(status(() -> Refresh.tokens(environment, environment.get(Value.SPENT_REFRESH_TOKEN))))
        .as("the spent refresh token").isEqualTo(GrpcStatus.UNAUTHENTICATED);
    assertThat(status(() -> Refresh.tokens(environment, environment.get(Value.REFRESH_TOKEN))))
        .as("the newest refresh token").isEqualTo(GrpcStatus.UNAUTHENTICATED);
    assertThat(status(() -> AuthenticateToken.principal(environment, environment.get(Value.ACCESS_TOKEN))))
        .as("the newest access token").isEqualTo(GrpcStatus.UNAUTHENTICATED);
  }
}
