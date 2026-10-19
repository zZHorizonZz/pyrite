package dev.horizon.pyrite.common;

import io.vertx.core.json.JsonObject;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A problem a library reports to its caller, as a problem details object of
 * <a href="https://www.rfc-editor.org/rfc/rfc9457">RFC 9457</a>.
 * <p>
 * It carries the five members the RFC defines:
 * <ul>
 * <li>{@link #type()}: a URI that identifies the problem type; each subclass has its own, and it leads to the
 * documentation of that type;</li>
 * <li>{@link #title()}: a short summary of the problem type, the same for every occurrence of it;</li>
 * <li>{@link #status()}: the HTTP status code a server answers with for it;</li>
 * <li>{@link #detail()}: what went wrong in this occurrence, which is also the exception's message;</li>
 * <li>{@link #instance()}: a URI that identifies this occurrence, when the caller gave one;</li>
 * </ul>
 * and its {@link #extensions()}: the members a subclass adds, such as the name of what was not found, and those the
 * caller declared. {@link #toJson()} writes them all as the body of an {@code application/problem+json} response.
 * <p>
 * Each subclass is one canonical code, so a host or a dialect maps a problem with one switch over the sealed
 * subclasses: {@link NotFoundException}, {@link AlreadyExistsException}, {@link AbortedException},
 * {@link InvalidArgumentException}, {@link FailedPreconditionException} and {@link UnimplementedException}.
 */
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

  /**
   * @param anchor the anchor of the problem type in {@code docs/reference/problem-types.md}
   * @param instance the URI of this occurrence, or {@code null} when there is none
   * @param own the extension members the subclass adds
   * @param declared the extension members the caller declared
   * @throws IllegalArgumentException when a declared member is one of the RFC's own or one the subclass adds
   */
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

  /**
   * @return the URI of the problem type
   */
  public URI type() {
    return type;
  }

  /**
   * @return the short summary of the problem type
   */
  public String title() {
    return title;
  }

  /**
   * @return the HTTP status code for the problem
   */
  public int status() {
    return status;
  }

  /**
   * @return what went wrong in this occurrence
   */
  public String detail() {
    return getMessage();
  }

  /**
   * @return the URI of this occurrence, when the caller gave one
   */
  public Optional<URI> instance() {
    return Optional.ofNullable(instance);
  }

  /**
   * @return the extension members, those of the subclass and those the caller declared
   */
  public JsonObject extensions() {
    return extensions.copy();
  }

  /**
   * @return the problem as an {@code application/problem+json} body: the RFC's members first, then the extensions
   */
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
