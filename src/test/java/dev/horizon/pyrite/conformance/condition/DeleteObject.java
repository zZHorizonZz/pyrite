package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.access.v1.DeleteObjectRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;

/**
 * Forgets a resource, as a subject.
 */
public record DeleteObject(String subject, String name) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.pyrite().access.deleteObject(DeleteObjectRequest.newBuilder().setSubject(subject).setName(name)
        .build()).await(TIMEOUT);
  }
}
