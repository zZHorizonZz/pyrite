package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.Acme;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.schema.v1.WriteDeclarationRequest;

/**
 * Writes the Acme declaration with a change of a type, and leaves its id.
 */
public record WriteTypeChange(TypeChange change) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.put(Value.DECLARATION_ID, environment.pyrite().schema.writeDeclaration(WriteDeclarationRequest
        .newBuilder().setDeclaration(change.apply(Acme.declaration())).build()).await(TIMEOUT).getId());
  }
}
