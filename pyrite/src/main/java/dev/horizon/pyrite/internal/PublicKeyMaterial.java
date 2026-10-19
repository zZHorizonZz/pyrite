package dev.horizon.pyrite.internal;

import java.security.PublicKey;
import java.util.Optional;

public record PublicKeyMaterial(Scheme scheme, PublicKey key) implements Material {

  @Override
  public Optional<byte[]> secret() {
    return Optional.empty();
  }

  @Override
  public Optional<byte[]> subjectPublicKeyInfo() {
    return Optional.of(key.getEncoded());
  }

  @Override
  public PublicKey publicKey() {
    return key;
  }
}
