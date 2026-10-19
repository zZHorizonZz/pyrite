package dev.horizon.pyrite.common;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.URI;
import java.util.List;

public final class InvalidArgumentException extends ProblemException {

  private final List<Violation> violations;

  public InvalidArgumentException(String detail, List<Violation> violations) {
    this(detail, violations, null, new JsonObject());
  }

  public InvalidArgumentException(String detail, List<Violation> violations, URI instance, JsonObject declared) {
    JsonArray errors = new JsonArray();
    for (Violation violation : violations) {
      errors.add(new JsonObject()
          .put("detail", violation.description())
          .put("pointer", "#/" + violation.field().replace('.', '/')));
    }
    super("invalid-argument", "Invalid argument", 400, detail, instance, new JsonObject().put("errors", errors),
        declared);
    this.violations = List.copyOf(violations);
  }

  public List<Violation> violations() {
    return violations;
  }
}
