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
 * Cuts the ciphertext of the last encryption short at each length, and checks that each cut is refused.
 */
public record ExpectTruncationRefusal(String key, String associatedData) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    ByteString sealed = environment.get(Value.CIPHERTEXT);
    for (int length = 0; length < sealed.size(); length++) {
      ByteString cut = sealed.substring(0, length);
      assertThat(status(() -> Decrypt.call(environment, ANNE, key, cut, associatedData))).as("%d bytes", length)
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }
}
