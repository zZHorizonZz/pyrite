package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.authentication.v1.DeleteAccountRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;

/**
 * Deletes an account, as a subject.
 */
public record DeleteAccount(String subject, String account) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.pyrite().authentication.deleteAccount(DeleteAccountRequest.newBuilder().setSubject(subject)
        .setName(account).build()).await(TIMEOUT);
  }
}
