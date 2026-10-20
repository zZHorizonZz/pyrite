package dev.horizon.pyrite.common;

import io.vertx.core.json.JsonObject;
import java.net.URI;

public final class FailedPreconditionException extends ProblemException {

  private final String reason;

  public FailedPreconditionException(String reason, String detail) {
    this(reason, detail, null, new JsonObject());
  }

  public FailedPreconditionException(String reason, String detail, URI instance, JsonObject declared) {
    super("failed-precondition", "Failed precondition", 400, detail, instance, new JsonObject().put("reason", reason),
        declared);
    this.reason = reason;
  }

  public String reason() {
    return reason;
  }
}
