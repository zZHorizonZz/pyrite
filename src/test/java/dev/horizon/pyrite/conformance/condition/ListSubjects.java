package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.access.v1.ListSubjectsRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Lists the subjects of a type that have a permission on an object, as a subject, and leaves them sorted.
 */
public record ListSubjects(String subject, String object, String permission, String subjectType)
    implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.put(Value.SUBJECTS, environment.pyrite().access.listSubjects(ListSubjectsRequest.newBuilder()
        .setSubject(subject).setObject(object).setPermission(permission).setSubjectType(subjectType).build())
        .await(TIMEOUT).getSubjectsList().stream().sorted().toList());
  }
}
