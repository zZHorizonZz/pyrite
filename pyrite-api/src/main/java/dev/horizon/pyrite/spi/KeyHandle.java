package dev.horizon.pyrite.spi;

import dev.horizon.pyrite.Export;
import dev.horizon.pyrite.Purpose;
import dev.horizon.pyrite.Rotation;
import dev.horizon.pyrite.State;
import dev.horizon.pyrite.common.Revision;
import io.vertx.core.Future;
import java.security.PublicKey;
import java.time.Instant;
import java.util.List;

/**
 * A key as its provider holds it. Every change names the revision it is made on and moves it.
 */
public interface KeyHandle {

  String name();

  Purpose<?> purpose();

  Rotation rotation();

  Revision revision();

  Instant created();

  /**
   * @return every version of the key, the newest first
   */
  Future<List<VersionHandle>> versions();

  /**
   * @return the key with {@code rotation}; it fails with {@code AbortedException} when the key is no longer at {@code expected}
   */
  Future<KeyHandle> rotation(Rotation rotation, Revision expected);

  /**
   * Makes a version whose material the provider generates and keeps.
   *
   * @return the version, in {@code state}; it fails with {@code AbortedException} when the key is no longer at {@code expected}
   */
  Future<VersionHandle> generate(State state, Revision expected);

  /**
   * Makes a version whose private half the provider gives away and does not keep.
   *
   * @return the version, in {@code state}, and its private key as PKCS#8; it fails with {@code AbortedException} when the key is
   * no longer at {@code expected}
   */
  Future<Export<VersionHandle>> export(State state, Revision expected);

  /**
   * Adds a version for a public key its holder made.
   *
   * @return the version, in {@code state}; it fails with {@code AbortedException} when the key is no longer at {@code expected}
   */
  Future<VersionHandle> upload(PublicKey publicKey, State state, Revision expected);
}
