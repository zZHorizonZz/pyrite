package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.PyriteFixture.status;
import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import io.vertx.grpc.common.GrpcStatus;

/**
 * Signs in again, and checks that the new session with the secret of the last one is refused, and so is the last
 * token cut short by a character.
 */
public record ExpectSecretBorrowingRefusal(String email, String password) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    String token = environment.get(Value.SESSION_TOKEN);
    String other = SignIn.token(environment, email, password);
    String borrowed = other.substring(0, other.indexOf('.')) + token.substring(token.indexOf('.'));
    assertThat(status(() -> AuthenticateToken.principal(environment, borrowed))).as(borrowed)
        .isEqualTo(GrpcStatus.UNAUTHENTICATED);
    String cut = token.substring(0, token.length() - 1);
    assertThat(status(() -> AuthenticateToken.principal(environment, cut))).as(cut)
        .isEqualTo(GrpcStatus.UNAUTHENTICATED);
  }
}
