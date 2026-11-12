package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.PyriteFixture.status;
import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import io.vertx.grpc.common.GrpcStatus;

/**
 * Changes each character of the secret of the last token in turn, and checks that each such token is refused.
 */
public record ExpectSecretForgeryRefusal() implements Condition {

  @Override
  public void evaluate(Environment environment) {
    String token = environment.get(Value.SESSION_TOKEN);
    for (int index = token.indexOf('.') + 1; index < token.length(); index++) {
      String forged = token.substring(0, index) + (token.charAt(index) == 'A' ? 'B' : 'A')
          + token.substring(index + 1);
      assertThat(status(() -> AuthenticateToken.principal(environment, forged))).as(forged)
          .isEqualTo(GrpcStatus.UNAUTHENTICATED);
    }
  }
}
