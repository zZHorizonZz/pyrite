package dev.horizon.pyrite.internal;

import dev.horizon.pyrite.Reason;
import dev.horizon.pyrite.common.FailedPreconditionException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public record AesMaterial(byte[] key) implements Material {

  public static final int KEY_BYTES = 32;
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final String CIPHER = "AES/GCM/NoPadding";
  private static final SecureRandom RANDOM = new SecureRandom();

  public AesMaterial {
    key = key.clone();
  }

  @Override
  public byte[] key() {
    return key.clone();
  }

  @Override
  public Optional<byte[]> secret() {
    return Optional.of(key.clone());
  }

  @Override
  public Optional<byte[]> subjectPublicKeyInfo() {
    return Optional.empty();
  }

  @Override
  public byte[] encrypt(byte[] plaintext, byte[] context) {
    byte[] nonce = new byte[NONCE_BYTES];
    RANDOM.nextBytes(nonce);
    try {
      Cipher cipher = Cipher.getInstance(CIPHER);
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
      cipher.updateAAD(context);
      byte[] sealed = cipher.doFinal(plaintext);
      return ByteBuffer.allocate(NONCE_BYTES + sealed.length).put(nonce).put(sealed).array();
    } catch (GeneralSecurityException refused) {
      throw new IllegalStateException("AES-GCM did not encrypt", refused);
    }
  }

  @Override
  public byte[] decrypt(byte[] ciphertext, byte[] context) {
    if (ciphertext.length < NONCE_BYTES) {
      throw new FailedPreconditionException(Reason.UNREADABLE.name(), "The ciphertext is too short");
    }
    try {
      Cipher cipher = Cipher.getInstance(CIPHER);
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
          new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(ciphertext, 0, NONCE_BYTES)));
      cipher.updateAAD(context);
      return cipher.doFinal(ciphertext, NONCE_BYTES, ciphertext.length - NONCE_BYTES);
    } catch (GeneralSecurityException unreadable) {
      throw new FailedPreconditionException(Reason.UNREADABLE.name(),
          "The ciphertext was not made by this key under that context, or was changed");
    }
  }
}
