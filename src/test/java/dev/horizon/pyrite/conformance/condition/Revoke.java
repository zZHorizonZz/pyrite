package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.access.v1.DeleteGrantRequest;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;

/**
 * Revokes a relation on an object from a grantee, as a subject.
 */
public record Revoke(String subject, String object, String relation, String grantee) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.pyrite().access.deleteGrant(DeleteGrantRequest.newBuilder().setSubject(subject).setGrant(Grant
        .newBuilder().setObject(object).setRelation(relation).setGrantee(grantee)).build()).await(TIMEOUT);
  }
}
