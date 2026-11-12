package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.authentication.v1.StartFlowRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;

/**
 * Starts a flow of a definition, as a subject, with an identifier, and leaves it as the last flow. An empty subject,
 * kind or identifier is none.
 */
public record StartFlow(String subject, String definition, String identifierKind, String identifier)
    implements Condition {

  /**
   * Starts a flow with an email address, or with no identifier for an empty one.
   */
  public StartFlow(String subject, String definition, String email) {
    this(subject, definition, email.isEmpty() ? "" : "email", email);
  }

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.start(StartFlowRequest.newBuilder().setSubject(subject).setDefinition(definition)
        .setIdentifierKind(identifierKind).setIdentifier(identifier).build());
  }
}
