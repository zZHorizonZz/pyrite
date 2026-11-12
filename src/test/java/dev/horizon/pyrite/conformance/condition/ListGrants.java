package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.access.v1.ListGrantsRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Lists the grants on an object, as a subject, of a relation and to a grantee when they are not empty, and leaves
 * them as relation and grantee, sorted.
 */
public record ListGrants(String subject, String object, String relation, String grantee) implements Condition {

  public ListGrants(String subject, String object) {
    this(subject, object, "", "");
  }

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.put(Value.GRANTS, environment.pyrite().access.listGrants(ListGrantsRequest.newBuilder()
        .setSubject(subject).setObject(object).setRelation(relation).setGrantee(grantee).build()).await(TIMEOUT)
        .getGrantsList().stream().map(grant -> grant.getRelation() + " " + grant.getGrantee()).sorted().toList());
  }
}
