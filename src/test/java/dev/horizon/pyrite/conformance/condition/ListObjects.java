package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import com.google.protobuf.Struct;
import dev.horizon.pyrite.access.v1.ListObjectsRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.List;

/**
 * Lists the objects of a type that a subject has a permission on, and leaves them.
 */
public record ListObjects(String subject, String type, String permission) implements Condition {

  /**
   * Lists the objects of a type that a subject has a permission on, in a context.
   */
  public static List<String> names(Environment environment, String subject, String type, String permission,
      Struct context) throws Exception {
    return environment.pyrite().access.listObjects(ListObjectsRequest.newBuilder().setSubject(subject).setType(type)
        .setPermission(permission).setContext(context).build()).await(TIMEOUT).getNamesList();
  }

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.put(Value.OBJECTS, names(environment, subject, type, permission, Struct.getDefaultInstance()));
  }
}
