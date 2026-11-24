package dev.horizon.pyrite.resource;

import dev.horizon.pyrite.declaration.TypeSystem;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Whom a grant goes to: an object, such as {@code pyrite.dev/Account:accounts/anne}, every object of a type,
 * {@code pyrite.dev/Account:*}, or the objects that have a relation on an object, such as
 * {@code acme.dev/Group:groups/finance#member}.
 *
 * @param object the object, whose id is {@code *} for every object of its type
 * @param relation the relation of a set of objects; empty for an object
 */
public record Grantee(ObjectRef object, String relation) {

  /**
   * Reads a grantee.
   *
   * @param text the grantee, such as {@code pyrite.dev/Account:accounts/anne}, {@code pyrite.dev/Account:*} or
   * {@code acme.dev/Group:groups/finance#member}
   * @return the grantee, or empty for text of none of these forms, or with an object, a relation or a length that
   * OpenFGA does not take: at most {@value Subject#MAX_BYTES} bytes, and the object of a set at most
   * {@value ObjectRef#MAX_LENGTH} characters
   */
  public static Optional<Grantee> parse(String text) {
    if (text.getBytes(StandardCharsets.UTF_8).length > Subject.MAX_BYTES) {
      return Optional.empty();
    }
    int hash = text.indexOf('#');
    if (hash >= 0) {
      String relation = text.substring(hash + 1);
      return TypeSystem.RELATION_NAME.matcher(relation).matches()
          ? ObjectRef.parse(text.substring(0, hash)).map(object -> new Grantee(object, relation))
          : Optional.empty();
    }
    if (text.endsWith(":*")) {
      String type = text.substring(0, text.length() - 2);
      return TypeSystem.TYPE_NAME.matcher(type).matches() ? Optional.of(new Grantee(new ObjectRef(type, "*"), ""))
          : Optional.empty();
    }
    return ObjectRef.split(text).map(object -> new Grantee(object, ""));
  }

  /**
   * Tells whether the grantee is the set of objects that have a relation on an object.
   */
  public boolean isUserset() {
    return !relation.isEmpty();
  }

  @Override
  public String toString() {
    return relation.isEmpty() ? object.toString() : object + "#" + relation;
  }
}
