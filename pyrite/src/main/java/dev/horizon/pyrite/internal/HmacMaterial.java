package dev.horizon.pyrite.internal;

import java.security.GeneralSecurityException;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public record HmacMaterial(byte[] key) implements Material {

  public static final int KEY_BYTES = 32;
  private static final String ALGORITHM = "HmacSHA256";

  public HmacMaterial {
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
  public byte[] mac(byte[] data) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(key, ALGORITHM));
      return mac.doFinal(data);
    } catch (GeneralSecurityException refused) {
      throw new IllegalStateException("HMAC-SHA256 did not run", refused);
    }
  }
}
