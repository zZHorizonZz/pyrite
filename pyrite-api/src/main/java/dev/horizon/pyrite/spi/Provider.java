package dev.horizon.pyrite.spi;

import dev.horizon.aquery.aip158.Page;
import dev.horizon.aquery.aip158.PageRequest;
import dev.horizon.pyrite.Capabilities;
import dev.horizon.pyrite.Purpose;
import dev.horizon.pyrite.Rotation;
import dev.horizon.pyrite.State;
import io.vertx.core.Future;

/**
 * Where keys and their versions live, and where their operations run.
 */
public interface Provider {

  /**
   * @return what the provider is called in a refusal, such as {@code database} or {@code gcpkms}
   */
  String name();

  /**
   * @return what the provider can hold and do; the keyring refuses everything else before it asks
   */
  Capabilities capabilities();

  /**
   * Stores a key with its first version, in {@code first}.
   *
   * @return the key; it fails with {@code AlreadyExistsException} when a key has that name
   */
  Future<KeyHandle> create(String name, Purpose<?> purpose, Rotation rotation, State first);

  /**
   * @return the key {@code name}; it fails with {@code NotFoundException} when no key has that name
   */
  Future<KeyHandle> key(String name);

  /**
   * @return a page of keys in the order of their names
   */
  Future<Page<KeyHandle>> keys(PageRequest request);
}
