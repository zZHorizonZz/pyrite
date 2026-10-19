package dev.horizon.pyrite;

import dev.horizon.aquery.aip158.Page;
import dev.horizon.aquery.aip158.PageRequest;
import dev.horizon.pyrite.spi.KeyringFactory;
import dev.horizon.pyrite.spi.Provider;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.ServiceLoader;

/**
 * The keys of an application, held by one provider. To keep some keys in one provider and others in another, create
 * one keyring for each.
 */
public interface Keyring {

  /**
   * @param provider what holds the keys and their versions
   * @return a keyring over {@code provider}
   * @throws IllegalStateException when no implementation of Pyrite is on the module path or the class path, which the
   * artifact {@code dev.horizon:pyrite} provides
   */
  static Keyring create(Vertx vertx, Provider provider) {
    return ServiceLoader.load(KeyringFactory.class)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No implementation of Pyrite is present; add dev.horizon:pyrite"))
        .keyring(vertx, provider);
  }

  /**
   * Creates a key with its first version, which is primary at once.
   *
   * @param name the name of the key: letters, digits, {@code -}, {@code _} and {@code .}, at most 63 of them
   * @param purpose what the key does; it decides the type of the key
   * @param rotation when the key gets a new primary version
   * @return the key; it fails with {@code AlreadyExistsException} when a key has that name, with {@code InvalidArgumentException} when the name
   * breaks the rule above, and with {@code UnimplementedException} when the provider does not
   * hold keys of that purpose or algorithm
   */
  <K extends Key> Future<K> create(String name, Purpose<K> purpose, Rotation rotation);

  /**
   * @return the encryption key {@code name}; it fails with {@code NotFoundException} when no key has that name, and with
   * {@code FailedPreconditionException}, {@link Reason#PURPOSE}, when the key has another purpose
   */
  Future<EncryptionKey> encryption(String name);

  /**
   * @return the MAC key {@code name}; it fails as {@link #encryption(String)} does
   */
  Future<MacKey> mac(String name);

  /**
   * @return the signing key {@code name}; it fails as {@link #encryption(String)} does
   */
  Future<SigningKey> signing(String name);

  /**
   * @param request the page to read; keys are in the order of their names, and {@code filter} and {@code orderBy} are
   * not supported
   * @return a page of keys, of every purpose
   */
  Future<Page<Key>> keys(PageRequest request);

  /**
   * @return what the provider of this keyring can hold and do
   */
  Capabilities capabilities();
}
