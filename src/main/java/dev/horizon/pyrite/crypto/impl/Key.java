package dev.horizon.pyrite.crypto.impl;

import dev.horizon.pyrite.impl.resource.Resource;
import io.vertx.core.Future;
import java.util.List;

/**
 * A key of the type {@code pyrite.dev/Key}: an {@link EncryptionKey}, or a {@link SigningKey} for the purposes
 * {@code MAC} and {@code SIGNING}.
 *
 * <p>
 * A key holds no material of its own. Its versions do, and the primary version makes new ciphertexts and signatures.
 */
public sealed interface Key extends Resource permits EncryptionKey, SigningKey {

  /**
   * Gives the key's purpose.
   *
   * @return {@code ENCRYPTION}, {@code MAC} or {@code SIGNING}
   */
  String purpose();

  /**
   * Gives the name of the primary version.
   *
   * @return the version's name, such as {@code keyVersions/payments-1}
   */
  String primaryVersion();

  /**
   * Reads the key's versions.
   *
   * @return the versions
   */
  Future<List<KeyVersion>> versions();

  /**
   * Reads one of the key's versions.
   *
   * @param version the version's name
   * @return the version, or a failure with {@code FAILED_PRECONDITION} when the key has no such version
   */
  Future<KeyVersion> version(String version);

  /**
   * Reads the primary version. A key whose {@code next_rotation_time} has passed rotates first.
   *
   * @return the primary version
   */
  Future<KeyVersion> primary();

  /**
   * Rotates the key: adds the next version and makes it the primary.
   *
   * @return the key, with its new primary version
   */
  Future<Key> rotate();
}
