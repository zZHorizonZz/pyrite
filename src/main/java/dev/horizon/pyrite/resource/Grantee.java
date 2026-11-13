package dev.horizon.pyrite.resource;

import java.util.Optional;

/**
 * Whom a grant goes to: an object, such as {@code user:anne}, every object of a type, {@code user:*}, or the objects
 * that have a relation on an object, such as {@code group:finance#member}.
 *
 * @param object the object, whose id is {@code *} for every object of its type
 * @param relation the relation of a set of objects; empty for an object
 */
public record Grantee(ObjectRef object, String relation) {

  /**
   * Reads a grantee.
   *
   * @param text the grantee, such as {@code user:anne}, {@code user:*} or {@code group:finance#member}
   * @return the grantee, or empty for text of none of these forms
   */
  public static Optional<Grantee> parse(String text) {
    int hash = text.indexOf('#');
    String relation = hash < 0 ? "" : text.substring(hash + 1);
    if (hash >= 0 && relation.isEmpty()) {
      return Optional.empty();
    }
    return ObjectRef.parse(hash < 0 ? text : text.substring(0, hash)).filter(object -> relation.isEmpty()
        || !object.id().equals("*")).map(object -> new Grantee(object, relation));
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
