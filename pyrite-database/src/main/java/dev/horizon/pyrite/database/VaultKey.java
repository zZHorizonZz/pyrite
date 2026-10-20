package dev.horizon.pyrite.database;

import com.google.crypto.tink.Aead;
import com.google.protobuf.ByteString;
import dev.horizon.basepoint.Filter;
import dev.horizon.cinder.DuplicateKeyException;
import dev.horizon.pyrite.common.NotFoundException;
import dev.horizon.pyrite.database.schema.v1.Tables;
import dev.horizon.pyrite.internal.AesMaterial;
import io.vertx.core.Future;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

record VaultKey(VaultBackend vaults, VersionBackend versions, Aead root) {

  private static final String NAME = "default";
  private static final byte[] ASSOCIATED_DATA = "pyrite vault".getBytes(StandardCharsets.UTF_8);
  private static final SecureRandom RANDOM = new SecureRandom();

  Future<AesMaterial> load() {
    return vaults.findById(NAME)
        .compose(found -> found.map(Future::succeededFuture).orElseGet(this::make))
        .map(row -> new AesMaterial(decrypt(row.getSealed().toByteArray())));
  }

  Future<Void> rewrap() {
    return vaults.findById(NAME)
        .map(found -> found.orElseThrow(() -> new NotFoundException("vault key", NAME)))
        .compose(row -> vaults.update(row.toBuilder()
            .setSealed(ByteString.copyFrom(encrypt(decrypt(row.getSealed().toByteArray()))))
            .build(), "sealed"))
        .mapEmpty();
  }

  private Future<Tables.Vault> make() {
    return versions.find(Filter.isNotNull("sealed")).mask("key_name,number").first().compose(sealed -> {
      if (sealed.isPresent()) {
        return Future.failedFuture(new IllegalStateException("The database holds sealed versions but no vault key, "
            + "so a new vault key could not read them; restore the pyrite_vault table from a backup"));
      }
      byte[] key = new byte[AesMaterial.KEY_BYTES];
      RANDOM.nextBytes(key);
      return vaults.insert(Tables.Vault.newBuilder().setName(NAME).setSealed(ByteString.copyFrom(encrypt(key))).build())
          .recover(failure -> failure instanceof DuplicateKeyException
              ? vaults.findById(NAME).map(found -> found.orElseThrow(() -> new NotFoundException("vault key", NAME)))
              : Future.failedFuture(failure));
    });
  }

  private byte[] encrypt(byte[] key) {
    try {
      return root.encrypt(key, ASSOCIATED_DATA);
    } catch (GeneralSecurityException refused) {
      throw new IllegalStateException("The root key did not encrypt the vault key", refused);
    }
  }

  private byte[] decrypt(byte[] sealed) {
    try {
      return root.decrypt(sealed, ASSOCIATED_DATA);
    } catch (GeneralSecurityException refused) {
      throw new IllegalStateException("The root key does not decrypt the vault key; the root keyset may no longer hold "
          + "the key that encrypted it", refused);
    }
  }
}
