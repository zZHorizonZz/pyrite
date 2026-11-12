package dev.horizon.pyrite.conformance.condition;

import com.google.protobuf.ByteString;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Keeps the ciphertext of the last encryption aside, so that a later encryption does not replace it.
 */
public record KeepCiphertext(Value<ByteString> into) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    environment.put(into, environment.get(Value.CIPHERTEXT));
  }
}
