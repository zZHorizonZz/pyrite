package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import io.vertx.grpc.common.GrpcStatus;

/**
 * Changes each byte of the ciphertext of the last encryption in turn, and checks that each such ciphertext is
 * refused.
 */
public record ExpectByteFlipRefusal(String key, String associatedData) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    byte[] sealed = environment.get(Value.CIPHERTEXT).toByteArray();
    for (int index = 0; index < sealed.length; index++) {
      byte[] changed = sealed.clone();
      changed[index] ^= 1;
      assertThat(status(() -> Decrypt.call(environment, ANNE, key, ByteString.copyFrom(changed), associatedData)))
          .as("byte %d", index).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }
}
