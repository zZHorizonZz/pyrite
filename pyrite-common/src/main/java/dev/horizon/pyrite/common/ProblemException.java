package dev.horizon.pyrite.common;

import io.vertx.core.json.JsonObject;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public abstract sealed class ProblemException extends RuntimeException permits NotFoundException,
    AlreadyExistsException, AbortedException, InvalidArgumentException, FailedPreconditionException,
    UnimplementedException {

  private static final Set<String> MEMBERS = Set.of("type", "title", "status", "detail", "instance");
  private static final String TYPES = "https://github.com/zZHorizonZz/pyrite/blob/main/docs/reference/problem-types.md#";

  private final URI type;
  private final String title;
  private final int status;
  private final URI instance;
  private final JsonObject extensions;

  protected ProblemException(String anchor, String title, int status, String detail, URI instance, JsonObject own,
      JsonObject declared) {
    super(Objects.requireNonNull(detail, "detail"));
    this.type = URI.create(TYPES + anchor);
    this.title = title;
    this.status = status;
    this.instance = instance;
    JsonObject members = own.copy();
    for (String name : declared.fieldNames()) {
      if (MEMBERS.contains(name) || members.containsKey(name)) {
        throw new IllegalArgumentException("The member " + name + " is not an extension member a caller may declare");
      }
      members.put(name, declared.getValue(name));
    }
    this.extensions = members;
  }

  public URI type() {
    return type;
  }

  public String title() {
    return title;
  }

  public int status() {
    return status;
  }

  public String detail() {
    return getMessage();
  }

  public Optional<URI> instance() {
    return Optional.ofNullable(instance);
  }

  public JsonObject extensions() {
    return extensions.copy();
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject()
        .put("type", type.toString())
        .put("title", title)
        .put("status", status)
        .put("detail", detail());
    if (instance != null) {
      json.put("instance", instance.toString());
    }
    return json.mergeIn(extensions.copy());
  }
}
