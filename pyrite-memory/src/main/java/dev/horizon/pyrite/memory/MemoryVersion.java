package dev.horizon.pyrite.memory;

import dev.horizon.pyrite.Origin;
import dev.horizon.pyrite.State;
import dev.horizon.pyrite.common.Revision;
import dev.horizon.pyrite.spi.VersionHandle;
import io.vertx.core.Future;
import java.security.PublicKey;
import java.time.Instant;

record MemoryVersion(MemoryEntry entry, String id, State state, Origin origin, Instant created, Instant expires)
    implements VersionHandle {

  @Override
  public Future<VersionHandle> state(State state, Revision expected) {
    return Future.succeededFuture().map(ignored -> entry.state(id, state, expected));
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
