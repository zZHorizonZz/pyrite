package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.crypto.v1.Key;

/**
 * Checks that the last key is a number of revisions after a key kept aside.
 */
public record ExpectKeyRevision(Value<Key> from, long later) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(Value.KEY).getRevision()).isEqualTo(environment.get(from).getRevision() + later);
  }
}
