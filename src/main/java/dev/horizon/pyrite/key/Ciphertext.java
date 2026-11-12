package dev.horizon.pyrite.key;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * A ciphertext of an encryption key: the id of the version that encrypted, after its length in one byte, so that a
 * decryption finds the version, and then what AES-GCM sealed, its nonce first.
 *
 * @param version the version that encrypted
 * @param body what AES-GCM made of the plaintext
 */
record Ciphertext(KeyVersionName version, byte[] body) {

  /**
   * Reads a ciphertext.
   *
   * @return the ciphertext, or empty for bytes that no version made
   */
  static Optional<Ciphertext> parse(byte[] bytes) {
    int length = bytes.length == 0 ? 0 : Byte.toUnsignedInt(bytes[0]);
    if (length == 0 || bytes.length <= 1 + length) {
      return Optional.empty();
    }
    return KeyVersionName.fromId(new String(bytes, 1, length, StandardCharsets.US_ASCII))
        .map(version -> new Ciphertext(version, Arrays.copyOfRange(bytes, 1 + length, bytes.length)));
  }

  byte[] bytes() {
    byte[] id = version.id().getBytes(StandardCharsets.US_ASCII);
    return ByteBuffer.allocate(1 + id.length + body.length).put((byte) id.length).put(id).put(body).array();
  }
}
