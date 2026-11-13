package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.PyriteFixture.status;
import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;

/**
 * Checks that text that is not a token, such as the id of the last refresh token without its secret, is refused.
 */
public record ExpectTokenSyntax() implements Condition {

  @Override
  public void evaluate(Environment environment) {
    String token = environment.get(Value.REFRESH_TOKEN);
    for (String text : List.of("", ".", "x", "x.", ".x", "x.y", token.substring(0, token.indexOf('.')),
        "00000000-0000-0000-0000-000000000000." + token.substring(token.indexOf('.') + 1))) {
      assertThat(status(() -> Refresh.tokens(environment, text))).as(text)
          .isEqualTo(GrpcStatus.UNAUTHENTICATED);
    }
  }
}
