package dev.horizon.pyrite.resource;

import dev.horizon.pyrite.declaration.TypeSystem;
import java.util.Optional;

/**
 * An object of OpenFGA, {@code type:id}: the name of a type, and an id, such as
 * {@code acme.dev/Project:organizations/acme/projects/ledger}. A resource that Pyrite knows is named by its object.
 *
 * @param type the name of the type, such as {@code acme.dev/Project}
 * @param id the id, such as {@code organizations/acme/projects/ledger}
 */
public record ObjectRef(String type, String id) {

  /** The most characters of an object, as OpenFGA takes it. */
  public static final int MAX_LENGTH = 256;

  /**
   * Reads an object, as OpenFGA takes it: a type that matches {@link TypeSystem#TYPE_NAME}, a {@code :}, and an id
   * other than {@code *} without {@code :}, {@code #}, white space or control characters; at most
   * {@value #MAX_LENGTH} characters in all.
   *
   * @param text the object, such as {@code acme.dev/Project:organizations/acme/projects/ledger}
   * @return the object, or empty for text that breaks a rule
   */
  public static Optional<ObjectRef> parse(String text) {
    return text.codePointCount(0, text.length()) > MAX_LENGTH ? Optional.empty() : split(text);
  }

  /**
   * Reads the type and the id of an object, as {@link #parse(String)} does, without the limit on its length.
   */
  static Optional<ObjectRef> split(String text) {
    int colon = text.indexOf(':');
    if (colon < 0 || !TypeSystem.TYPE_NAME.matcher(text.substring(0, colon)).matches()) {
      return Optional.empty();
    }
    String id = text.substring(colon + 1);
    return id.isEmpty() || id.equals("*") || id.codePoints().anyMatch(c -> c == ':' || c == '#'
        || Character.isWhitespace(c) || Character.isISOControl(c)) ? Optional.empty()
            : Optional.of(new ObjectRef(text.substring(0, colon), id));
  }

  @Override
  public String toString() {
    return type + ":" + id;
  }
}
