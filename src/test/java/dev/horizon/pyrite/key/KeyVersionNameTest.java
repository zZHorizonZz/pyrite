package dev.horizon.pyrite.key;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KeyVersionNameTest {

  @Test
  @DisplayName("a name splits at its last hyphen, so a key id may hold hyphens")
  void parse() {
    assertThat(KeyVersionName.parse("keyVersions/ledger-stock-12")).contains(new KeyVersionName("ledger-stock", 12));
    assertThat(new KeyVersionName("ledger-stock", 12).id()).isEqualTo("ledger-stock-12");
    assertThat(new KeyVersionName("ledger-stock", 12).name()).isEqualTo("keyVersions/ledger-stock-12");
  }

  @Test
  @DisplayName("text that is not the name or id of a version is refused")
  void refused() {
    for (String text : new String[] { "keys/payments-1", "keyVersions/payments", "keyVersions/payments-0",
        "keyVersions/payments-01", "keyVersions/-1", "keyVersions/payments-1x" }) {
      assertThat(KeyVersionName.parse(text)).as(text).isEmpty();
    }
    assertThat(KeyVersionName.fromId("payments-1234567890123456789")).isEmpty();
  }

  @Test
  @DisplayName("a ciphertext reads back the version and the body that it was made of")
  void ciphertext() {
    Ciphertext made = new Ciphertext(new KeyVersionName("ledger-stock", 3), new byte[] { 1, 2, 3 });

    assertThat(Ciphertext.parse(made.bytes())).hasValueSatisfying(read -> {
      assertThat(read.version()).isEqualTo(made.version());
      assertThat(read.body()).containsExactly(1, 2, 3);
    });
    assertThat(Ciphertext.parse(new byte[0])).isEmpty();
    assertThat(Ciphertext.parse(prefixed(8, "payments..."))).as("an id without a number").isEmpty();
    assertThat(Ciphertext.parse(prefixed(10, "payments-1"))).as("an id with nothing after it").isEmpty();
  }

  /**
   * Gives text after a byte that says how long the id in it is.
   */
  private static byte[] prefixed(int length, String text) {
    byte[] bytes = new byte[1 + text.length()];
    bytes[0] = (byte) length;
    System.arraycopy(text.getBytes(StandardCharsets.US_ASCII), 0, bytes, 1, text.length());
    return bytes;
  }
}
