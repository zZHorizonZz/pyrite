package dev.horizon.pyrite;

import dev.horizon.pyrite.common.Revision;
import io.vertx.core.Future;
import java.time.Instant;
import java.util.List;

/**
 * A named key with one purpose, one rotation and its versions. A key is what was read when it was read; its methods
 * act on the key as it is when they run.
 */
public sealed interface Key permits EncryptionKey, MacKey, SigningKey {

  /**
   * @return the name of the key, unique in its keyring
   */
  String name();

  /**
   * @return what the key does
   */
  Purpose<?> purpose();

  /**
   * @return when the key gets a new primary version
   */
  Rotation rotation();

  /**
   * @return the revision the key was read at; every new version and every change of a version moves it
   */
  Revision revision();

  /**
   * @return when the key was created
   */
  Instant created();

  /**
   * @return the version that encrypts, fingerprints or signs now
   */
  Future<Version> primary();

  /**
   * @return every version of the key, the newest first, including disabled and destroyed ones
   */
  Future<List<Version>> versions();

  /**
   * @return the version {@code id} of this key; it fails with {@code NotFoundException} when the key has no such version
   */
  Future<Version> version(String id);

  /**
   * Makes a new version primary now, whatever the rotation says. A signing key's new version is primary only after
   * its {@link Signing#ahead()}, as with every rotation.
   *
   * @return the new version
   */
  Future<Version> rotate();

  /**
   * @param rotation the rotation from now on; it changes no existing version
   * @param expected the revision the change is made on
   * @return the key as changed; it fails with {@code AbortedException} when the key is no longer at {@code expected}
   */
  Future<Key> rotation(Rotation rotation, Revision expected);
}
