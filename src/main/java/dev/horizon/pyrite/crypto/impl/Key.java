package dev.horizon.pyrite.crypto.impl;

import dev.horizon.pyrite.impl.resource.Resource;
import io.vertx.core.Future;
import java.time.Duration;
import java.util.List;

/**
 * A key, the resource {@code pyrite.dev/Key}: an encryption key or a key that signs. The primary version encrypts and
 * signs; every enabled version decrypts and verifies.
 */
public sealed interface Key permits EncryptionKey, SigningKey {

  /**
   * Gives the key's name.
   *
   * @return the name, such as {@code keys/payments}
   */
  String name();

  /**
   * Gives the key as a resource, which grants and checks reach.
   *
   * @return the resource
   */
  Resource resource();

  /**
   * Gives the name of the version that encrypts and signs.
   *
   * @return the name, such as {@code keyVersions/payments-2}
   */
  String primaryVersion();

  /**
   * Gives the versions of the key.
   *
   * @return the versions, oldest first
   */
  Future<List<KeyVersion>> versions();

  /**
   * Gives the primary version, after rotating the key when its rotation is due.
   *
   * @return the primary version
   */
  Future<KeyVersion> primary();

  /**
   * Makes a new version and makes it the primary one.
   *
   * @return the key, with its new primary version
   */
  Future<Key> rotate();

  /**
   * Changes how often the key rotates on its own.
   *
   * @param period the period; zero for never
   * @param revision the revision that the change is made from
   * @return the changed key, or a failure with {@code ABORTED} when the key changed since the revision
   */
  Future<Key> update(Duration period, long revision);

  /**
   * Gives the key as a message.
   *
   * @return the message of {@code pyrite.crypto.v1}
   */
  dev.horizon.pyrite.crypto.v1.Key message();
}
