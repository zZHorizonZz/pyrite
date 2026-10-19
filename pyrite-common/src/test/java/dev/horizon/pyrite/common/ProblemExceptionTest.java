package dev.horizon.pyrite.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProblemExceptionTest {

  @Test
  void problemIsWrittenAsProblemDetails() {
    NotFoundException problem = new NotFoundException("key", "sessions", URI.create("urn:request:7f3a"),
        new JsonObject().put("keyring", "app"));

    assertThat(problem.toJson()).isEqualTo(new JsonObject()
        .put("type", "https://github.com/zZHorizonZz/pyrite/blob/main/docs/reference/problem-types.md#not-found")
        .put("title", "Not found")
        .put("status", 404)
        .put("detail", "No key is named sessions")
        .put("instance", "urn:request:7f3a")
        .put("resourceType", "key")
        .put("resourceName", "sessions")
        .put("keyring", "app"));
  }

  @Test
  void violationsAreWrittenAsErrorsWithPointers() {
    InvalidArgumentException problem = new InvalidArgumentException("The key is not valid",
        List.of(new Violation("signing.algorithm", "one of RS256, ES256 and EDDSA")));

    assertThat(problem.toJson().getJsonArray("errors")).isEqualTo(new JsonArray()
        .add(new JsonObject().put("detail", "one of RS256, ES256 and EDDSA").put("pointer", "#/signing/algorithm")));
    assertThat(problem.instance()).isEmpty();
  }

  @Test
  void declaredMemberCannotReplaceOneOfTheProblem() {
    assertThatThrownBy(() -> new FailedPreconditionException("PRIMARY", "The version is primary", null,
        new JsonObject().put("status", 200))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FailedPreconditionException("PRIMARY", "The version is primary", null,
        new JsonObject().put("reason", "OTHER"))).isInstanceOf(IllegalArgumentException.class);
  }
}
