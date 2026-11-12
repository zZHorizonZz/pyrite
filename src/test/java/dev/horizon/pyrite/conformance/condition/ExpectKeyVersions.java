package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.crypto.v1.KeyVersion;
import java.util.List;

/**
 * Checks the names of the versions of the last list, in order, and that each is numbered from 1.
 */
public record ExpectKeyVersions(List<String> names) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    List<KeyVersion> versions = environment.get(Value.KEY_VERSIONS);
    assertThat(versions).extracting(KeyVersion::getName).containsExactlyElementsOf(names);
    for (int index = 0; index < versions.size(); index++) {
      assertThat(versions.get(index).getNumber()).as("number of %s", names.get(index)).isEqualTo(index + 1L);
    }
  }
}
