package dev.horizon.pyrite;

import dev.horizon.pyrite.common.Revision;
import io.vertx.core.Future;
import java.time.Instant;
import java.util.List;

/**
 * A named key with a purpose, a rotation, a validity and versions. A key holds the values that were current when it was
 * read. Its methods read the key again before they act.
 */
public sealed interface Key permits EncryptionKey, MacKey, SigningKey {

  /**
   * Returns the name of the key, which is unique in its keyring.
   */
  String name();

  Purpose<?> purpose();

  /**
   * Returns when the key gets a new primary version.
   */
  Rotation rotation();

  /**
   * Returns how long each new version of the key works.
   */
  Validity validity();

  /**
   * Returns the revision of the key when it was read. Each new version and each change to a version increments it.
   */
  Revision revision();

  Instant created();

  /**
   * Returns the version that encrypts, computes MACs or signs.
   */
  Future<Version> primary();

  /**
   * Returns every version of the key, newest first, in every state.
   */
  Future<List<Version>> versions();

  /**
   * Returns the version with the id. The future fails with {@code NotFoundException} if the key has no such version.
   */
  Future<Version> version(String id);

  /**
   * Creates a new version, regardless of the rotation. The new version is pending for the {@link Validity#pending()} of
   * the key, then becomes primary. If a version is already pending, returns that version.
   */
  Future<Version> rotate();

  /**
   * Changes the rotation of the key. Existing versions do not change.
   *
   * <p>
   * The future fails with {@code AbortedException} if the key changed after {@code expected}, and with
   * {@code InvalidArgumentException} if the rotation does not fit the validity of the key.
   */
  Future<Key> rotation(Rotation rotation, Revision expected);

  /**
   * Changes the validity of the key. It applies to versions created after the change. Existing versions keep their
   * expiry time.
   *
   * <p>
   * The future fails with {@code AbortedException} if the key changed after {@code expected}, and with
   * {@code InvalidArgumentException} if the validity does not fit the rotation of the key.
   */
  Future<Key> validity(Validity validity, Revision expected);
}
