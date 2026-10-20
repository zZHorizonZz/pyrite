package dev.horizon.pyrite.common;

import io.vertx.core.json.JsonObject;
import java.net.URI;

public final class AbortedException extends ProblemException {

  private final String resourceType;
  private final String resourceName;
  private final Revision expected;

  public AbortedException(String resourceType, String resourceName, Revision expected) {
    this(resourceType, resourceName, expected, null, new JsonObject());
  }

  public AbortedException(String resourceType, String resourceName, Revision expected, URI instance,
      JsonObject declared) {
    super("aborted", "Aborted", 409,
        "The " + resourceType + " " + resourceName + " is no longer at revision " + expected.value(), instance,
        new JsonObject().put("resourceType", resourceType)
            .put("resourceName", resourceName)
            .put("expectedRevision", expected.value()),
        declared);
    this.resourceType = resourceType;
    this.resourceName = resourceName;
    this.expected = expected;
  }

  public String resourceType() {
    return resourceType;
  }

  public String resourceName() {
    return resourceName;
  }

  public Revision expected() {
    return expected;
  }
}
