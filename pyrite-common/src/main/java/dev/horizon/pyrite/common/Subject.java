package dev.horizon.pyrite.common;

import io.vertx.core.json.JsonObject;
import java.util.Objects;

public record Subject(String type, String id, JsonObject properties) {

  public Subject {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(properties, "properties");
    if (type.isBlank()) {
      throw new IllegalArgumentException("A subject has a type");
    }
    if (id.isBlank()) {
      throw new IllegalArgumentException("A subject has an id");
    }
    properties = properties.copy();
  }

  public Subject(String type, String id) {
    this(type, id, new JsonObject());
  }

  @Override
  public JsonObject properties() {
    return properties.copy();
  }
}
