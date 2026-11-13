package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Encrypts the same plaintext with a key a number of times, and checks that every ciphertext is different.
 */
public record ExpectRandomEncryption(String key, int count) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    Set<ByteString> seen = new HashSet<>();
    for (int index = 0; index < count; index++) {
      seen.add(Encrypt.call(environment, ANNE, key, "card".getBytes(StandardCharsets.UTF_8), "a").getCiphertext());
    }
    assertThat(seen).hasSize(count);
  }
}
