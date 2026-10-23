package dev.horizon.pyrite.crypto.impl;

import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.impl.resource.BaseResource;
import dev.horizon.pyrite.impl.resource.Resource;
import dev.horizon.pyrite.impl.storage.Row;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Map;

final class KeyVersionImpl extends BaseResource implements KeyVersion {

  static final String ENABLED = "ENABLED";
  static final String DISABLED = "DISABLED";

  KeyVersionImpl(KeyVersionTypeImpl type, Row row) {
    super(type, row);
  }

  @Override
  public KeyVersionTypeImpl type() {
    return (KeyVersionTypeImpl) super.type();
  }

  @Override
  public long number() {
    return field(KeyVersionType.NUMBER);
  }

  @Override
  public boolean isUsable() {
    return ENABLED.equals(field(KeyVersionType.STATE)) && !isDeleted();
  }

  @Override
  public Future<Key> key() {
    return parent().map(parent -> (Key) parent.orElseThrow());
  }

  @Override
  public Future<Resource> update(Map<String, Object> changes, long revision) {
    Object state = changes.get(KeyVersionType.STATE);
    if (state == null || state.equals(ENABLED)) {
      return super.update(changes, revision);
    }
    if (!state.equals(DISABLED)) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "A version is ENABLED or DISABLED, not " + state + ".");
    }
    return requireSecondary().compose(ignored -> super.update(changes, revision));
  }

  @Override
  public Future<Resource> delete(long revision) {
    return requireSecondary().compose(ignored -> super.delete(revision));
  }

  Algorithm algorithm() {
    return Algorithm.valueOf(field(KeyVersionType.ALGORITHM));
  }

  byte[] sealedSecret() {
    return field(KeyVersionType.SEALED);
  }

  byte[] publicKeyInfo() {
    return field(KeyVersionType.PUBLIC_KEY_INFO);
  }

  byte[] sign(byte[] data) {
    return ((Material.Signing) type().material(this)).sign(data);
  }

  boolean verifies(byte[] data, byte[] signature) {
    return ((Material.Signing) type().material(this)).verifies(data, signature);
  }

  Material.Aes cipher() {
    return (Material.Aes) type().material(this);
  }

  private Future<Void> requireSecondary() {
    return key().compose(key -> key.primaryVersion().equals(name())
        ? Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The version " + name() + " is"
            + " the primary version of its key; rotate the key first."))
        : Future.succeededFuture());
  }
}
