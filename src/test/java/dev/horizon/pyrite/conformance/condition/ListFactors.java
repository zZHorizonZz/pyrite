package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.authentication.v1.ListFactorsRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;

/**
 * Lists the factors of an account, as a subject.
 */
public record ListFactors(String subject, String account) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.pyrite().authentication.listFactors(ListFactorsRequest.newBuilder().setSubject(subject)
        .setAccount(account).build()).await(TIMEOUT);
  }
}
