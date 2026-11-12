package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import java.util.List;

/**
 * Encrypts an empty, a binary and a large plaintext with a key, and checks that each decrypts to itself.
 */
public record ExpectRoundTrips(String key) implements Condition {

  private static final int BINARY = 256;
  private static final int LARGE = 1 << 20;

  @Override
  public void evaluate(Environment environment) throws Exception {
    byte[] binary = new byte[BINARY];
    for (int index = 0; index < binary.length; index++) {
      binary[index] = (byte) index;
    }
    for (byte[] plaintext : List.of(new byte[0], binary, new byte[LARGE])) {
      ByteString sealed = Encrypt.call(environment, ANNE, key, plaintext, "").getCiphertext();
      assertThat(Decrypt.call(environment, ANNE, key, sealed, "").toByteArray()).as("%d bytes", plaintext.length)
          .isEqualTo(plaintext);
    }
  }
}
