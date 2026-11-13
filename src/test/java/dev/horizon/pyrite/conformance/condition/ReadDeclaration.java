package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.schema.v1.ReadDeclarationRequest;

/**
 * Reads the declaration of an id, or the latest for an empty one, and leaves it.
 */
public record ReadDeclaration(String id) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.put(Value.DECLARATION, environment.pyrite().schema.readDeclaration(ReadDeclarationRequest
        .newBuilder().setId(id).build()).await(TIMEOUT));
  }
}
