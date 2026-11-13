package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.Acme;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.schema.v1.WriteDeclarationRequest;
import dev.horizon.pyrite.schema.v1.WriteDeclarationResponse;
import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the Acme declaration a number of times at once, each without waiting for the others, and waits until every
 * write succeeds.
 */
public record WriteDeclarationsAtOnce(int count) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    WriteDeclarationRequest request = WriteDeclarationRequest.newBuilder().setDeclaration(Acme.declaration()).build();
    List<Future<WriteDeclarationResponse>> writes = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      writes.add(environment.pyrite().schema.writeDeclaration(request));
    }
    Future.all(writes).await(TIMEOUT);
  }
}
