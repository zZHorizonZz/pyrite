package dev.horizon.pyrite.conformance.condition;

import com.google.protobuf.ByteString;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Makes a forgery of the last signature: an empty one, or one with its first byte changed.
 */
public record ForgeSignature(boolean empty) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    if (empty) {
      environment.put(Value.SIGNATURE_FORGERY, ByteString.EMPTY);
      return;
    }
    byte[] changed = environment.get(Value.SIGNATURE).toByteArray();
    changed[0] ^= 1;
    environment.put(Value.SIGNATURE_FORGERY, ByteString.copyFrom(changed));
  }
}
