package dev.horizon.pyrite;

import io.vertx.core.Future;
import java.time.Instant;

/**
 * One generation of a key. A version is what was read when it was read; its methods act on it as it is when they run.
 */
public interface Version {

  /**
   * @return the id of the version, unique in its key
   */
  String id();

  /**
   * @return where the version is in its life
   */
  State state();

  /**
   * @return how the version was made
   */
  Origin origin();

  /**
   * @return when the version was made
   */
  Instant created();

  /**
   * Stops the version from decrypting, matching and verifying, until it is enabled again.
   *
   * @return the version as changed; it fails with {@code FailedPreconditionException}, {@link Reason#PRIMARY}, for the primary version,
   * which a rotation replaces first
   */
  Future<Version> disable();

  /**
   * @return the version as changed; it fails with {@code FailedPreconditionException}, {@link Reason#VERSION}, unless it is disabled
   */
  Future<Version> enable();

  /**
   * Destroys the material of the version. What it made can no longer be decrypted, matched or verified, and that
   * cannot be undone.
   *
   * @return the version as changed; it fails with {@code FailedPreconditionException}, {@link Reason#PRIMARY}, for the primary version
   */
  Future<Version> destroy();
}
