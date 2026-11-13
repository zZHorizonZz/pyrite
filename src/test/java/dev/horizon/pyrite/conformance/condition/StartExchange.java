package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.authentication.v1.StartExchangeRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;

/**
 * Starts an exchange of a definition, as a subject, with an identifier, and leaves it as the last exchange. An empty
 * subject, kind or identifier is none.
 */
public record StartExchange(String subject, String definition, String identifierKind, String identifier)
    implements Condition {

  /**
   * Starts an exchange with an email address, or with no identifier for an empty one.
   */
  public StartExchange(String subject, String definition, String email) {
    this(subject, definition, email.isEmpty() ? "" : "email", email);
  }

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.start(StartExchangeRequest.newBuilder().setSubject(subject).setDefinition(definition)
        .setIdentifierKind(identifierKind).setIdentifier(identifier).build());
  }
}
