package dev.horizon.pyrite;

import io.vertx.core.Future;
import java.time.Instant;
import java.util.Optional;

/**
 * One generation of the material of a key. A version holds the values that were current when it was read. Its methods
 * read the version again before they act.
 */
public interface Version {

  /**
   * Returns the id of the version, which is unique in its key.
   */
  String id();

  State state();

  Origin origin();

  Instant created();

  /**
   * Returns when the version expires, or nothing if it does not expire. The expiry time is set when the version is
   * created.
   */
  Optional<Instant> expires();

  /**
   * Disables the version. A disabled version does not decrypt, check MACs or verify until it is enabled.
   *
   * <p>
   * The future fails with {@code FailedPreconditionException} and the reason {@link Reason#PRIMARY} if the version
   * is primary. Rotate the key first.
   */
  Future<Version> disable();

  /**
   * Enables a disabled version.
   *
   * <p>
   * The future fails with {@code FailedPreconditionException} and the reason {@link Reason#VERSION} if the version is
   * not disabled or has expired.
   */
  Future<Version> enable();

  /**
   * Deletes the material of the version. Data that the version encrypted, MACs that it computed and signatures that it
   * made can no longer be decrypted or checked. This cannot be undone.
   *
   * <p>
   * The future fails with {@code FailedPreconditionException} and the reason {@link Reason#PRIMARY} if the version
   * is primary.
   */
  Future<Version> destroy();
}
