package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.access.v1.CreateGrantRequest;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Grants a relation on an object to a grantee, as a subject, and leaves the grant.
 */
public record GrantRelation(String subject, String object, String relation, String grantee) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.put(Value.GRANT, environment.pyrite().access.createGrant(CreateGrantRequest.newBuilder()
        .setSubject(subject).setGrant(Grant.newBuilder().setObject(object).setRelation(relation).setGrantee(grantee))
        .build()).await(TIMEOUT));
  }
}
