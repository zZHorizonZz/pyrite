package dev.horizon.pyrite.internal;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Optional;

public record KeyPairMaterial(Scheme scheme, KeyPair pair) implements Material {

  @Override
  public Optional<byte[]> secret() {
    return Optional.of(pair.getPrivate().getEncoded());
  }

  @Override
  public Optional<byte[]> subjectPublicKeyInfo() {
    return Optional.of(pair.getPublic().getEncoded());
  }

  @Override
  public byte[] sign(byte[] data) {
    return scheme.sign(pair.getPrivate(), data);
  }

  @Override
  public PublicKey publicKey() {
    return pair.getPublic();
  }
}
