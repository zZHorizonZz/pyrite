package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.authentication.v1.GetAccountRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Reads an account, as a subject, and leaves it.
 */
public record GetAccount(String subject, String name) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.put(Value.ACCOUNT, environment.pyrite().authentication.getAccount(GetAccountRequest.newBuilder()
        .setSubject(subject).setName(name).build()).await(TIMEOUT));
  }
}
