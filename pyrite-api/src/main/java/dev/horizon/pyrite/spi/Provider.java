package dev.horizon.pyrite.spi;

import dev.horizon.aquery.aip158.Page;
import dev.horizon.aquery.aip158.PageRequest;
import dev.horizon.pyrite.Capabilities;
import dev.horizon.pyrite.Purpose;
import dev.horizon.pyrite.Rotation;
import dev.horizon.pyrite.State;
import dev.horizon.pyrite.Validity;
import io.vertx.core.Future;
import java.time.Instant;

/**
 * Stores keys and versions, and runs the operations of each version. Implement it to store keys somewhere new.
 *
 * <p>
 * The keyring decides when versions are created, which version is primary, and how data is formatted. A provider
 * stores what the keyring gives it. Every change names the revision of the key that it applies to, and fails with
 * {@code AbortedException} if the key changed after that revision.
 */
public interface Provider {

  /**
   * Returns the name of the provider for error messages, such as {@code database}.
   */
  String name();

  /**
   * Returns what the provider supports. The keyring refuses other operations before it calls the provider.
   */
  Capabilities capabilities();

  /**
   * Stores a key with its first version in the state {@code first}. The future fails with
   * {@code AlreadyExistsException} if a key has the name.
   *
   * @param expires when the first version expires, or {@link Instant#MAX} if it does not expire
   */
  Future<KeyHandle> create(String name, Purpose<?> purpose, Rotation rotation, Validity validity, State first,
      Instant expires);

  /**
   * Returns the key with the name. The future fails with {@code NotFoundException} if no key has the name.
   */
  Future<KeyHandle> key(String name);

  /**
   * Returns a page of keys, ordered by name.
   */
  Future<Page<KeyHandle>> keys(PageRequest request);
}
