package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.authentication.v1.GetSessionRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Reads the last session, as a subject, and leaves what the read gave.
 */
public record GetSession(String subject) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.put(Value.SESSION_READ, environment.pyrite().authentication.getSession(GetSessionRequest
        .newBuilder().setSubject(subject).setName(environment.get(Value.SESSION)).build()).await(TIMEOUT));
  }
}
