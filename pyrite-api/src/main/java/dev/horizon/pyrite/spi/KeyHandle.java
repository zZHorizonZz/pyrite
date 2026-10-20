package dev.horizon.pyrite.spi;

import dev.horizon.pyrite.Export;
import dev.horizon.pyrite.Purpose;
import dev.horizon.pyrite.Rotation;
import dev.horizon.pyrite.State;
import dev.horizon.pyrite.Validity;
import dev.horizon.pyrite.common.Revision;
import io.vertx.core.Future;
import java.security.PublicKey;
import java.time.Instant;
import java.util.List;

/**
 * A key as its provider stores it. Each change fails with {@code AbortedException} if the key changed after
 * {@code expected}, and otherwise increments the revision of the key.
 */
public interface KeyHandle {

  String name();

  Purpose<?> purpose();

  Rotation rotation();

  Validity validity();

  Revision revision();

  Instant created();

  /**
   * Returns every version of the key, newest first.
   */
  Future<List<VersionHandle>> versions();

  Future<KeyHandle> rotation(Rotation rotation, Revision expected);

  Future<KeyHandle> validity(Validity validity, Revision expected);

  /**
   * Creates a version whose material the provider generates and stores.
   *
   * @param expires when the version expires, or {@link Instant#MAX} if it does not expire
   */
  Future<VersionHandle> generate(State state, Instant expires, Revision expected);

  /**
   * Creates a version and returns its private key in PKCS#8. The provider stores only the public key.
   *
   * @param expires when the version expires, or {@link Instant#MAX} if it does not expire
   */
  Future<Export<VersionHandle>> export(State state, Instant expires, Revision expected);

  /**
   * Adds a version for a public key that its holder created.
   *
   * @param expires when the version expires, or {@link Instant#MAX} if it does not expire
   */
  Future<VersionHandle> upload(PublicKey publicKey, State state, Instant expires, Revision expected);
}
