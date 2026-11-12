package dev.horizon.pyrite.key;

import com.google.protobuf.ByteString;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.crypto.v1.DecryptResponse;
import dev.horizon.pyrite.crypto.v1.EncryptResponse;
import dev.horizon.pyrite.schema.keys.v1.Tables;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * A key whose purpose is {@code ENCRYPTION}: AES-256 in GCM mode.
 *
 * <p>
 * A ciphertext names the version that made it, and is bound to the key's name and the caller's associated data. It
 * decrypts only with the same key and the same associated data.
 */
public final class EncryptionKey extends Key {

  EncryptionKey(KeyType type, Tables.Key row) {
    super(type, row);
  }

  /**
   * Encrypts with the primary version, after rotating the key when its rotation is due.
   *
   * @param associatedData the data that a decryption names again, such as the id of the row that stores the
   * ciphertext
   * @return the ciphertext and the version's name
   */
  public Future<EncryptResponse> encrypt(byte[] plaintext, byte[] associatedData) {
    return primary().map(version -> EncryptResponse.newBuilder().setKeyVersion(version.name())
        .setCiphertext(ByteString.copyFrom(new Ciphertext(version.versionName(), version.encrypt(plaintext,
            bound(associatedData))).bytes()))
        .build());
  }

  /**
   * Decrypts with the version that encrypted.
   *
   * @param associatedData the data that the encryption named
   * @return the plaintext and the version's name, or a failure with {@code FAILED_PRECONDITION} when the version is
   * disabled or destroyed, and with {@code INVALID_ARGUMENT} when the ciphertext is not this key's for this data
   */
  public Future<DecryptResponse> decrypt(byte[] ciphertext, byte[] associatedData) {
    Optional<Ciphertext> parsed = Ciphertext.parse(ciphertext);
    if (parsed.isEmpty()) {
      return Future.failedFuture(foreign());
    }
    KeyVersionName version = parsed.get().version();
    return type().versions().findByName(version.name()).compose(found -> {
      if (found.isEmpty() || !found.get().getParent().equals(name())) {
        return Future.failedFuture(destroyed(version) ? new PyriteException(GrpcStatus.FAILED_PRECONDITION,
            "The version " + version + " that encrypted is destroyed.") : foreign());
      }
      KeyVersion used = new KeyVersion(type(), found.get());
      if (!used.isUsable()) {
        return Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The version " + version
            + " that encrypted is disabled."));
      }
      return Future.succeededFuture(DecryptResponse.newBuilder().setKeyVersion(version.name()).setPlaintext(
          ByteString.copyFrom(used.decrypt(parsed.get().body(), bound(associatedData)))).build());
    });
  }

  /**
   * Tells whether a version of this key that no row holds was destroyed: a version is numbered up to the primary one.
   */
  private boolean destroyed(KeyVersionName version) {
    return version.key().equals(id())
        && KeyVersionName.parse(primaryVersion()).map(primary -> version.number() <= primary.number()).orElse(false);
  }

  private PyriteException foreign() {
    return new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The ciphertext was not made by the key " + name() + ".");
  }

  /**
   * Binds associated data to the key's name, so that a ciphertext does not decrypt with another key.
   */
  private byte[] bound(byte[] associatedData) {
    byte[] name = (name() + '\0').getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(name.length + associatedData.length).put(name).put(associatedData).array();
  }
}
