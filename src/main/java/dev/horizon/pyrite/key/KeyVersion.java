package dev.horizon.pyrite.key;

import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.schema.keys.v1.Tables;
import dev.horizon.pyrite.storage.DatabaseTime;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.GrpcStatus;

/**
 * A version of a key: its material, sealed by the root key, and its state. A disabled version neither decrypts nor
 * verifies.
 *
 * <p>
 * A version is named after its key and its number, such as {@code keyVersions/payments-2}. It is not a declared type:
 * a call on a version checks the relations of its key.
 */
public final class KeyVersion {

  private final KeyType type;
  private final Tables.KeyVersion row;

  KeyVersion(KeyType type, Tables.KeyVersion row) {
    this.type = type;
    this.row = row;
  }

  /**
   * Gives the version's name.
   *
   * @return the name, such as {@code keyVersions/payments-2}
   */
  public String name() {
    return row.getName();
  }

  /**
   * Gives the version's id, which a ciphertext and a JWT's key id name.
   *
   * @return the id, such as {@code payments-2}
   */
  public String id() {
    return versionName().id();
  }

  /**
   * Gives the version's number among the versions of its key, from 1.
   */
  public long number() {
    return row.getNumber();
  }

  /**
   * Tells whether the version decrypts and verifies, which an enabled one does.
   */
  public boolean isUsable() {
    return row.getState() == Tables.KeyVersion.State.ENABLED;
  }

  public Future<Key> key() {
    return type.key(row.getParent());
  }

  /**
   * Enables or disables the version. The primary version is not disabled.
   *
   * @param enabled whether the version decrypts and verifies after the change
   * @param revision the revision that the change is made from
   * @return the changed version, or a failure with {@code ABORTED} when the version changed since the revision, and
   * with {@code FAILED_PRECONDITION} when it disables the primary version
   */
  public Future<KeyVersion> update(boolean enabled, long revision) {
    if (row.getRevision() != revision) {
      return Future.failedFuture(new PyriteException(GrpcStatus.ABORTED, "The key version " + name()
          + " is at revision " + row.getRevision() + ", not " + revision + "."));
    }
    Future<Void> allowed = enabled ? Future.succeededFuture() : requireSecondary();
    return allowed.compose(ignored -> type.versions().update(row.toBuilder().setState(enabled
        ? Tables.KeyVersion.State.ENABLED
        : Tables.KeyVersion.State.DISABLED).build(), row.getUid(),
        row.getRevision(), name())).map(changed -> new KeyVersion(type, changed));
  }

  /**
   * Destroys the version: deletes its row, with its sealed material. Nothing that it encrypted decrypts again.
   *
   * @return the deletion, or a failure with {@code FAILED_PRECONDITION} for the primary version
   */
  public Future<Void> delete() {
    return requireSecondary().compose(ignored -> type.versions().deleteById(row.getUid())).mapEmpty();
  }

  /**
   * Gives the version as the service returns it.
   *
   * @return the version, with its public key as a JWK for a version of the purpose {@code SIGNING}
   */
  public dev.horizon.pyrite.crypto.v1.KeyVersion message() {
    dev.horizon.pyrite.crypto.v1.KeyVersion.Builder message = dev.horizon.pyrite.crypto.v1.KeyVersion.newBuilder()
        .setName(name()).setNumber(number())
        .setState(dev.horizon.pyrite.crypto.v1.KeyVersion.State.valueOf(row.getState().name()))
        .setAlgorithm(dev.horizon.pyrite.crypto.v1.Algorithm.valueOf(row.getAlgorithm().name()))
        .setRevision(row.getRevision()).setCreateTime(DatabaseTime.timestamp(row.getCreateTime()));
    if (row.hasPublicKey()) {
      message.setPublicKey(new ClaimSet(new JsonObject(row.getPublicKey())).struct());
    }
    return message.build();
  }

  KeyVersionName versionName() {
    return KeyVersionName.parse(name()).orElseThrow(() -> new IllegalStateException("The row of a key version has"
        + " the name " + name() + ", which is not a version's"));
  }

  Algorithm algorithm() {
    return Algorithm.valueOf(row.getAlgorithm().name());
  }

  byte[] secretCiphertext() {
    return row.getSealed().toByteArray();
  }

  /**
   * Gives the public key as X.509 SubjectPublicKeyInfo, or null for a secret key.
   */
  byte[] publicKeyInfo() {
    return row.hasPublicKeyInfo() ? row.getPublicKeyInfo().toByteArray() : null;
  }

  byte[] encrypt(byte[] plaintext, byte[] associatedData) {
    return aes().encrypt(plaintext, associatedData);
  }

  byte[] decrypt(byte[] sealed, byte[] associatedData) {
    return aes().decrypt(sealed, associatedData);
  }

  byte[] sign(byte[] data) {
    return signing().sign(data);
  }

  boolean verifies(byte[] data, byte[] signature) {
    return signing().verifies(data, signature);
  }

  private Material.Aes aes() {
    if (type.material(this) instanceof Material.Aes aes) {
      return aes;
    }
    throw new IllegalStateException("The version " + name() + " of the algorithm " + algorithm() + " does not"
        + " encrypt");
  }

  private Material.Signing signing() {
    if (type.material(this) instanceof Material.Signing signing) {
      return signing;
    }
    throw new IllegalStateException("The version " + name() + " of the algorithm " + algorithm() + " does not sign");
  }

  private Future<Void> requireSecondary() {
    return type.keys().findByName(row.getParent()).compose(key -> key.isPresent()
        && key.get().getPrimaryVersion().equals(name())
            ? Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The version " + name()
                + " is the primary version of its key; rotate the key first."))
            : Future.succeededFuture());
  }
}
