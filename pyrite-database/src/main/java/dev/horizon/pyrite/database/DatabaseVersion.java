package dev.horizon.pyrite.database;

import dev.horizon.pyrite.Origin;
import dev.horizon.pyrite.Reason;
import dev.horizon.pyrite.State;
import dev.horizon.pyrite.common.FailedPreconditionException;
import dev.horizon.pyrite.common.Revision;
import dev.horizon.pyrite.database.schema.v1.Tables;
import dev.horizon.pyrite.internal.Material;
import dev.horizon.pyrite.internal.Vault;
import dev.horizon.pyrite.internal.VersionName;
import dev.horizon.pyrite.spi.VersionHandle;
import io.vertx.core.Future;
import java.security.PublicKey;
import java.time.Instant;

final class DatabaseVersion implements VersionHandle {

  private static final StateMapper STATE_MAPPER = new StateMapper();
  private static final ExpiryMapper EXPIRY_MAPPER = new ExpiryMapper();
  private static final String STATE = "state,state_time,sealed";

  private final DatabaseKey key;
  private final Vault vault;
  private final Tables.Version row;
  private final VersionName name;
  private final State state;

  DatabaseVersion(DatabaseKey key, Vault vault, Tables.Version row) {
    this.key = key;
    this.vault = vault;
    this.row = row;
    this.name = new VersionName(row.getKeyName(), row.getNumber());
    this.state = STATE_MAPPER.read(row);
  }

  @Override
  public String id() {
    return String.valueOf(row.getNumber());
  }

  @Override
  public State state() {
    return state;
  }

  @Override
  public Origin origin() {
    return Origin.valueOf(row.getOrigin());
  }

  @Override
  public Instant created() {
    return Instant.parse(row.getCreateTime());
  }

  @Override
  public Instant expires() {
    return EXPIRY_MAPPER.read(row);
  }

  @Override
  public Future<VersionHandle> state(State state, Revision expected) {
    Tables.Version.Builder changed = STATE_MAPPER.write(row.toBuilder(), state);
    if (state instanceof State.Destroyed) {
      changed.clearSealed();
    }
    vault.forget(name);
    return key.updated(changed.build(), STATE, expected);
  }

  @Override
  public Future<byte[]> encrypt(byte[] plaintext, byte[] context) {
    return Future.succeededFuture().map(ignored -> material().encrypt(plaintext, context));
  }

  @Override
  public Future<byte[]> decrypt(byte[] ciphertext, byte[] context) {
    return Future.succeededFuture().map(ignored -> material().decrypt(ciphertext, context));
  }

  @Override
  public Future<byte[]> mac(byte[] data) {
    return Future.succeededFuture().map(ignored -> material().mac(data));
  }

  @Override
  public Future<byte[]> sign(byte[] data) {
    return Future.succeededFuture().map(ignored -> material().sign(data));
  }

  @Override
  public Future<PublicKey> publicKey() {
    return Future.succeededFuture().map(ignored -> material().publicKey());
  }

  private Material material() {
    if (state instanceof State.Destroyed) {
      throw new FailedPreconditionException(Reason.VERSION.name(), "The version " + name + " is destroyed");
    }
    return vault.unseal(name, key.purpose(), row.getSealed().toByteArray(), row.getPublicKey().toByteArray());
  }
}
