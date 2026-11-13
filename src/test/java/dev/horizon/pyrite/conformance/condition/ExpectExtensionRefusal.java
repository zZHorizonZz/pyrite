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
 * Adds a byte to the ciphertext of the last encryption, and checks that it is refused.
 */
public record ExpectExtensionRefusal(String key, String associatedData) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    ByteString extended = environment.get(Value.CIPHERTEXT).concat(ByteString.copyFromUtf8("x"));
    assertThat(status(() -> Decrypt.call(environment, ANNE, key, extended, associatedData)))
        .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
  }
}
