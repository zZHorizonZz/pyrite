package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.access.v1.CreateObjectRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;

/**
 * Makes a resource known under a parent, as a subject. An empty parent is none.
 */
public record CreateObject(String subject, String name, String parent) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.pyrite().access.createObject(CreateObjectRequest.newBuilder().setSubject(subject).setName(name)
        .setParent(parent).build()).await(TIMEOUT);
  }
}
