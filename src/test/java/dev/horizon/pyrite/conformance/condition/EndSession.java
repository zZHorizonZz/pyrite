package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.authentication.v1.EndSessionRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Ends the last session, as a subject.
 */
public record EndSession(String subject) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.pyrite().authentication.endSession(EndSessionRequest.newBuilder().setSubject(subject)
        .setName(environment.get(Value.SESSION)).build()).await(TIMEOUT);
  }
}
