package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.authentication.v1.ListIdentifiersRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.List;

/**
 * Lists the identifiers of an account, as a subject, and leaves them.
 */
public record ListIdentifiers(String subject, String account) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.put(Value.IDENTIFIERS, List.copyOf(environment.pyrite().authentication.listIdentifiers(
        ListIdentifiersRequest.newBuilder().setSubject(subject).setAccount(account).build()).await(TIMEOUT)
        .getIdentifiersList()));
  }
}
