package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;

/**
 * Decrypts bytes that no key made with a key, and checks that each is refused.
 */
public record ExpectForeignBytesRefusal(String key) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    for (String garbage : List.of("", "x", "\u0005payments-1", "\u00ffpayments-1abc")) {
      assertThat(status(() -> Decrypt.call(environment, ANNE, key, ByteString.copyFromUtf8(garbage), "a")))
          .as(garbage).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }
}
