package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import io.vertx.grpc.common.GrpcStatus;
import java.nio.charset.StandardCharsets;

/**
 * Renames the key version that the ciphertext of the last encryption names to another version of its key, and checks
 * that it is refused.
 */
public record ExpectVersionRenameRefusal(String key, String associatedData, String from, String to)
    implements Condition {

  @Override
  public void evaluate(Environment environment) {
    byte[] renamed = environment.get(Value.CIPHERTEXT).toByteArray();
    String header = new String(renamed, 1, renamed[0], StandardCharsets.US_ASCII);
    byte[] other = header.replace(from, to).getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(other, 0, renamed, 1, other.length);
    assertThat(status(() -> Decrypt.call(environment, ANNE, key, ByteString.copyFrom(renamed), associatedData)))
        .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
  }
}
