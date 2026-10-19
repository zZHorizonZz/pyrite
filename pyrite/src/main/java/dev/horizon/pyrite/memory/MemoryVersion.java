package dev.horizon.pyrite.memory;

import dev.horizon.pyrite.Origin;
import dev.horizon.pyrite.State;
import dev.horizon.pyrite.common.Revision;
import dev.horizon.pyrite.spi.VersionHandle;
import io.vertx.core.Future;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Optional;

record MemoryVersion(MemoryEntry entry, String id, State state, Origin origin, Instant created, byte[] held)
    implements VersionHandle {

  @Override
  public Optional<byte[]> certificate() {
    return Optional.ofNullable(held).map(byte[]::clone);
  }

  @Override
  public Future<VersionHandle> state(State state, Revision expected) {
    return Future.succeededFuture().map(ignored -> entry.state(id, state, expected));
  }

  @Override
  public Future<VersionHandle> certificate(byte[] certificate, Revision expected) {
    return Future.succeededFuture().map(ignored -> entry.certificate(id, certificate, expected));
  }

  @Override
  public Future<byte[]> encrypt(byte[] plaintext, byte[] context) {
    return Future.succeededFuture().map(ignored -> entry.material(id).encrypt(plaintext, context));
  }

  @Override
  public Future<byte[]> decrypt(byte[] ciphertext, byte[] context) {
    return Future.succeededFuture().map(ignored -> entry.material(id).decrypt(ciphertext, context));
  }

  @Override
  public Future<byte[]> mac(byte[] data) {
    return Future.succeededFuture().map(ignored -> entry.material(id).mac(data));
  }

  @Override
  public Future<byte[]> sign(byte[] data) {
    return Future.succeededFuture().map(ignored -> entry.material(id).sign(data));
  }

  @Override
  public Future<PublicKey> publicKey() {
    return Future.succeededFuture().map(ignored -> entry.material(id).publicKey());
  }
}
