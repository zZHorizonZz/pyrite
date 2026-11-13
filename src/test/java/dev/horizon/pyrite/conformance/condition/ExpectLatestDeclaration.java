package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.schema.v1.ReadDeclarationRequest;

/**
 * Reads the latest declaration, and checks that it is the last one written, or, when nothing was to change it, the
 * last one read.
 */
public record ExpectLatestDeclaration(boolean changed) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    String latest = environment.pyrite().schema.readDeclaration(ReadDeclarationRequest.getDefaultInstance())
        .await(TIMEOUT).getId();
    String before = environment.get(Value.DECLARATION).getId();
    if (changed) {
      assertThat(latest).isEqualTo(environment.get(Value.DECLARATION_ID)).isNotEqualTo(before);
    } else {
      assertThat(latest).isEqualTo(before);
    }
  }
}
