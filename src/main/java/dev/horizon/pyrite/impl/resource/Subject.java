package dev.horizon.pyrite.impl.resource;

import dev.horizon.pyrite.PyriteException;
import io.vertx.grpc.common.GrpcStatus;
import java.util.regex.Pattern;

public record Subject(String type, String id) {

  private static final Pattern TYPE = Pattern.compile("[a-z][a-z0-9_]{0,49}");

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
