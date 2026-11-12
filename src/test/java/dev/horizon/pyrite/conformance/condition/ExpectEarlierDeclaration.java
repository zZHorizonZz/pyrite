package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.Acme;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.schema.v1.ReadDeclarationRequest;
import dev.horizon.pyrite.schema.v1.ReadDeclarationResponse;

/**
 * Reads the last declaration read again by its id, and checks that it is the Acme declaration as it was written, with
 * its time.
 */
public record ExpectEarlierDeclaration() implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    ReadDeclarationResponse first = environment.get(Value.DECLARATION);
    ReadDeclarationResponse again = environment.pyrite().schema.readDeclaration(ReadDeclarationRequest.newBuilder()
        .setId(first.getId()).build()).await(TIMEOUT);
    assertThat(again.getDeclaration()).as("declaration").isEqualTo(Acme.declaration());
    assertThat(again.getCreateTime()).as("time").isEqualTo(first.getCreateTime());
  }
}
