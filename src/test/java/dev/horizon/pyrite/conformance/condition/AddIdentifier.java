package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.authentication.v1.AddIdentifierRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Adds an identifier to an account, as a subject, and leaves it. An empty kind is none.
 */
public record AddIdentifier(String subject, String account, String kind, String value) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.put(Value.IDENTIFIER, environment.pyrite().authentication.addIdentifier(AddIdentifierRequest
        .newBuilder().setSubject(subject).setAccount(account).setKind(kind).setValue(value).build()).await(TIMEOUT));
  }
}
