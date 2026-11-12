package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.access.v1.ListChangesRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Lists the changes of the grants on the resources of a type, as a subject, and leaves them as relation, grantee and
 * operation.
 */
public record ListChanges(String subject, String type) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.put(Value.CHANGES, environment.pyrite().access.listChanges(ListChangesRequest.newBuilder()
        .setSubject(subject).setType(type).build()).await(TIMEOUT).getChangesList().stream().map(change -> change
            .getGrant().getRelation() + " " + change.getGrant().getGrantee() + " " + change.getOperation())
        .toList());
  }
}
