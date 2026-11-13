package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.PyriteFixture.status;
import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.GrpcStatus;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Checks that an access token whose claims or signature were changed is refused: one that names another session or
 * lasts a year longer, one with the first character of its signature changed, one without its signature, and one that claims
 * no algorithm.
 */
public record ExpectAccessTokenForgeryRefusal() implements Condition {

  @Override
  public void evaluate(Environment environment) {
    String token = environment.get(Value.ACCESS_TOKEN);
    String[] parts = token.split("\\.");
    JsonObject claims = new JsonObject(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
    String otherSession = encode(claims.copy().put("sid", "sessions/00000000-0000-0000-0000-000000000000"));
    String longer = encode(claims.copy().put("exp", claims.getLong("exp") + 365L * 24 * 3600));
    String none = encode(new JsonObject().put("alg", "none").put("typ", "at+jwt"));
    char first = parts[2].charAt(0);
    for (String forged : List.of(parts[0] + "." + otherSession + "." + parts[2],
        parts[0] + "." + longer + "." + parts[2],
        parts[0] + "." + parts[1] + "." + (first == 'A' ? 'B' : 'A') + parts[2].substring(1),
        parts[0] + "." + parts[1] + ".",
        none + "." + parts[1] + ".")) {
      assertThat(status(() -> AuthenticateToken.principal(environment, forged))).as(forged)
          .isEqualTo(GrpcStatus.UNAUTHENTICATED);
    }
  }

  private static String encode(JsonObject json) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(json.encode().getBytes(StandardCharsets.UTF_8));
  }
}
