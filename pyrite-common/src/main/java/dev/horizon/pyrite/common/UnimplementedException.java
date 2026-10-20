package dev.horizon.pyrite.common;

import io.vertx.core.json.JsonObject;
import java.net.URI;

public final class UnimplementedException extends ProblemException {

  public UnimplementedException(String detail) {
    this(detail, null, new JsonObject());
  }

  public UnimplementedException(String detail, URI instance, JsonObject declared) {
    super("unimplemented", "Unimplemented", 501, detail, instance, new JsonObject(), declared);
  }
}
