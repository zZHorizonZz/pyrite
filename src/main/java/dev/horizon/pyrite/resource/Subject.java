package dev.horizon.pyrite.resource;

import dev.horizon.pyrite.PyriteException;
import io.vertx.grpc.common.GrpcStatus;
import java.util.regex.Pattern;

/**
 * The principal that a call runs as, or that a check asks about, such as {@code user:anne}. Pyrite does not verify
 * it: the caller sets it from a credential that it verified.
 *
 * @param type the OpenFGA type, such as {@code user}
 * @param id the id, such as {@code anne}
 */
public record Subject(String type, String id) {

  private static final Pattern TYPE = Pattern.compile("[a-z][a-z0-9_]{0,49}");

  /**
   * Reads a principal.
   *
   * @param text the principal, such as {@code user:anne}
   * @return the principal
   * @throws PyriteException with {@code INVALID_ARGUMENT} for text that is not {@code type:id}, with a type that
   * matches {@code [a-z][a-z0-9_]{0,49}} and an id without {@code #}, {@code *}, white space or control characters
   */
  public static Subject parse(String text) {
    int colon = text.indexOf(':');
    if (colon < 0 || !TYPE.matcher(text.substring(0, colon)).matches() || colon == text.length() - 1
        || text.chars().anyMatch(c -> c == '#' || c == '*' || Character.isWhitespace(c) || Character.isISOControl(c))) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The subject '" + text + "' is not a principal of the"
          + " form type:id, such as user:anne.");
    }
    return new Subject(text.substring(0, colon), text.substring(colon + 1));
  }

  @Override
  public String toString() {
    return type + ":" + id;
  }
}
