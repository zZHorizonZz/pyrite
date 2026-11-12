package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import com.google.protobuf.Struct;
import dev.horizon.pyrite.access.v1.CheckRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.Map;

/**
 * Checks a permission of a principal on an object, as a subject, in a context, and leaves the answer. An empty
 * principal is the subject itself.
 */
public record Check(String subject, String principal, String object, String permission, Map<String, String> context)
    implements Condition {

  /**
   * Checks a permission of a subject itself.
   */
  public Check(String subject, String object, String permission) {
    this(subject, "", object, permission, Map.of());
  }

  public Check(String subject, String principal, String object, String permission) {
    this(subject, principal, object, permission, Map.of());
  }

  /**
   * Gives the context of a check at a time.
   */
  public static Map<String, String> at(String time) {
    return Map.of("current_time", time);
  }

  @Override
  public void evaluate(Environment environment) throws Exception {
    Struct.Builder values = Struct.newBuilder();
    context.forEach((name, text) -> values.putFields(name, com.google.protobuf.Value.newBuilder()
        .setStringValue(text).build()));
    environment.put(Value.ALLOWED, environment.pyrite().access.check(CheckRequest.newBuilder().setSubject(subject)
        .setPrincipal(principal).setObject(object).setPermission(permission).setContext(values).build())
        .await(TIMEOUT).getAllowed());
  }
}
