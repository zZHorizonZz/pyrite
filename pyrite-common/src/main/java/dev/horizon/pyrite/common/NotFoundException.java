package dev.horizon.pyrite.common;

import io.vertx.core.json.JsonObject;
import java.net.URI;

public final class NotFoundException extends ProblemException {

  private final String resourceType;
  private final String resourceName;

  public NotFoundException(String resourceType, String resourceName) {
    this(resourceType, resourceName, null, new JsonObject());
  }

  public NotFoundException(String resourceType, String resourceName, URI instance, JsonObject declared) {
    super("not-found", "Not found", 404, "No " + resourceType + " is named " + resourceName, instance,
        new JsonObject().put("resourceType", resourceType).put("resourceName", resourceName), declared);
    this.resourceType = resourceType;
    this.resourceName = resourceName;
  }

  public String resourceType() {
    return resourceType;
  }

  public String resourceName() {
    return resourceName;
  }
}
