package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import com.google.protobuf.Struct;
import dev.horizon.pyrite.access.v1.CreateGrantRequest;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.Map;

/**
 * Grants a relation on an object to a grantee under a condition with values, as a subject. A value is text or a
 * number.
 */
public record GrantWithCondition(String subject, String object, String relation, String grantee, String condition,
    Map<String, Object> context) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    Struct.Builder values = Struct.newBuilder();
    context.forEach((name, given) -> values.putFields(name, given instanceof Number number
        ? com.google.protobuf.Value.newBuilder().setNumberValue(number.doubleValue()).build()
        : com.google.protobuf.Value.newBuilder().setStringValue(given.toString()).build()));
    environment.put(Value.GRANT, environment.pyrite().access.createGrant(CreateGrantRequest.newBuilder()
        .setSubject(subject).setGrant(Grant.newBuilder().setObject(object).setRelation(relation).setGrantee(grantee)
            .setCondition(dev.horizon.pyrite.access.v1.Condition.newBuilder().setName(condition)
                .setContext(values)))
        .build()).await(TIMEOUT));
  }
}
