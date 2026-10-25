package dev.horizon.pyrite.crypto.impl;

import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.schema.keys.v1.Tables;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.GrpcStatus;

final class KeyVersionImpl implements KeyVersion {

  static final String ENABLED = "ENABLED";
  static final String DISABLED = "DISABLED";

  private final KeyTypeImpl type;
  private final Tables.KeyVersion row;

  KeyVersionImpl(KeyTypeImpl type, Tables.KeyVersion row) {
    this.type = type;
    this.row = row;
  }

  @Override
  public String name() {
    return row.getName();
  }

  @Override
  public String id() {
    return name().substring(KeyTypeImpl.VERSIONS.length());
  }

  @Override
  public long number() {
    return row.getNumber();
  }

  @Override
  public boolean isUsable() {
    return ENABLED.equals(row.getState());
  }

  @Override
  public Future<Key> key() {
    return type.key(row.getParent());
  }

  @Override
  public Future<KeyVersion> update(boolean enabled, long revision) {
    if (row.getRevision() != revision) {
      throw new PyriteException(GrpcStatus.ABORTED, "The key version " + name() + " is at revision "
          + row.getRevision() + ", not " + revision + ".");
    }
    Future<Void> allowed = enabled ? Future.succeededFuture() : requireSecondary();
    return allowed.compose(ignored -> type.versions().update(row.toBuilder().setState(enabled ? ENABLED : DISABLED)
        .setRevision(row.getRevision() + 1).setUpdateTime(type.schema().clock().instant().toString()).build(),
        row.getUid(), row.getRevision(), name()))
        .map(changed -> new KeyVersionImpl(type, changed));
  }

  @Override
  public Future<Void> delete() {
    return requireSecondary().compose(ignored -> type.versions().deleteById(row.getUid())).mapEmpty();
  }

  @Override
  public dev.horizon.pyrite.crypto.v1.KeyVersion message() {
    dev.horizon.pyrite.crypto.v1.KeyVersion.Builder message = dev.horizon.pyrite.crypto.v1.KeyVersion.newBuilder()
        .setName(name()).setNumber(number())
        .setState(dev.horizon.pyrite.crypto.v1.KeyVersion.State.valueOf(row.getState()))
        .setAlgorithm(dev.horizon.pyrite.crypto.v1.Algorithm.valueOf(row.getAlgorithm()))
        .setRevision(row.getRevision()).setCreateTime(KeyImpl.timestamp(row.getCreateTime()));
    if (row.hasPublicKey()) {
      message.setPublicKey(new Claims(new JsonObject(row.getPublicKey())).struct());
    }
    return message.build();
  }

  Algorithm algorithm() {
    return Algorithm.valueOf(row.getAlgorithm());
  }

  byte[] sealedSecret() {
    return row.getSealed().toByteArray();
  }

  byte[] publicKeyInfo() {
    return row.hasPublicKeyInfo() ? row.getPublicKeyInfo().toByteArray() : null;
  }

  byte[] sign(byte[] data) {
    return ((Material.Signing) type.material(this)).sign(data);
  }

  boolean verifies(byte[] data, byte[] signature) {
    return ((Material.Signing) type.material(this)).verifies(data, signature);
  }

  Material.Aes cipher() {
    return (Material.Aes) type.material(this);
  }

  private Future<Void> requireSecondary() {
    return type.keys().findByName(row.getParent()).compose(key -> key.isPresent()
        && key.get().getPrimaryVersion().equals(name())
            ? Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The version " + name()
                + " is the primary version of its key; rotate the key first."))
            : Future.succeededFuture());
  }
}
