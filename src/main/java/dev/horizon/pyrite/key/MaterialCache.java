package dev.horizon.pyrite.key;

import dev.horizon.pyrite.schema.keys.v1.Tables;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The material of the key versions last used, unsealed. The material of a version stays in memory after its first use,
 * for the 256 versions last used; the root key unseals any other again when it is used.
 *
 * <p>
 * The cache holds the material by the uid of the version's row, so a version made again under a name that a deleted
 * version had never finds the material of the deleted one.
 */
final class MaterialCache {

  /**
   * How many versions' material stays unsealed in memory.
   */
  private static final int MAX_UNSEALED_VERSIONS = 256;

  private final RootKey root;
  /**
   * The unsealed material of the versions last used, by the uid of the version's row.
   */
  private final Map<String, Material> materials = Collections.synchronizedMap(new LinkedHashMap<>(
      MAX_UNSEALED_VERSIONS, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, Material> eldest) {
      return size() > MAX_UNSEALED_VERSIONS;
    }
  });

  MaterialCache(RootKey root) {
    this.root = root;
  }

  /**
   * Keeps the material of a version whose row is in the database.
   */
  void put(Tables.KeyVersion version, Material material) {
    materials.put(version.getUid(), material);
  }

  /**
   * Gives the AES key of a version of an encryption key.
   *
   * @throws IllegalStateException for a version of another algorithm
   */
  Material.Aes aes(Tables.KeyVersion version) {
    if (material(version) instanceof Material.Aes aes) {
      return aes;
    }
    throw new IllegalStateException("The version " + version.getName() + " of the algorithm "
        + version.getAlgorithm() + " does not encrypt");
  }

  /**
   * Gives the material of a version of a key that signs.
   *
   * @throws IllegalStateException for a version of an encryption key
   */
  Material.Signing signing(Tables.KeyVersion version) {
    if (material(version) instanceof Material.Signing signing) {
      return signing;
    }
    throw new IllegalStateException("The version " + version.getName() + " of the algorithm "
        + version.getAlgorithm() + " does not sign");
  }

  /**
   * Gives the material of a version, unsealing it when it is not in memory.
   */
  private Material material(Tables.KeyVersion version) {
    return materials.computeIfAbsent(version.getUid(), uid -> KeyAlgorithm.of(version.getAlgorithm()).restore(
        root.unseal(KeyVersionName.of(version), version.getSealed().toByteArray()),
        version.hasPublicKeyInfo() ? version.getPublicKeyInfo().toByteArray() : null));
  }
}
