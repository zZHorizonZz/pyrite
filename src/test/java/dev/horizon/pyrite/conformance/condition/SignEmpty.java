package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.crypto.v1.SignRequest;

/**
 * Asks to sign nothing with a key, as a subject.
 */
public record SignEmpty(String subject, String key) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.pyrite().cryptography.sign(SignRequest.newBuilder().setSubject(subject).setKey(key).build())
        .await(TIMEOUT);
  }
}
