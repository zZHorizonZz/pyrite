package dev.horizon.pyrite.resource;

import java.util.Optional;

/**
 * An object of OpenFGA, {@code type:id}: the singular name of a type, and the id of a resource of it.
 *
 * @param type the singular name of the type, such as {@code project}
 * @param id the id, such as {@code ledger}
 */
public record ObjectRef(String type, String id) {

  /**
   * Reads an object.
   *
   * @param text the object, such as {@code project:ledger}
   * @return the object, or empty for text that is not of the form type:id
   */
  public static Optional<ObjectRef> parse(String text) {
    int colon = text.indexOf(':');
    return colon <= 0 || colon == text.length() - 1 ? Optional.empty()
        : Optional.of(new ObjectRef(text.substring(0, colon), text.substring(colon + 1)));
  }

  @Override
  public String toString() {
    return type + ":" + id;
  }
}
