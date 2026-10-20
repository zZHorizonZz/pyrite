package dev.horizon.pyrite;

import dev.horizon.aquery.aip158.Page;
import dev.horizon.aquery.aip158.PageRequest;
import dev.horizon.pyrite.spi.KeyringFactory;
import dev.horizon.pyrite.spi.Provider;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.ServiceLoader;

/**
 * The keys of an application, stored by one provider. To store keys with two providers, create a keyring for each.
 */
public interface Keyring {

  /**
   * Creates a keyring that stores its keys with {@code provider}.
   *
   * @throws IllegalStateException if no implementation of Pyrite is on the module path or the class path. Each provider
   * artifact, such as {@code dev.horizon:pyrite-database}, includes one.
   */
  static Keyring create(Vertx vertx, Provider provider) {
    return ServiceLoader.load(KeyringFactory.class)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No implementation of Pyrite is present; add a provider, such as "
            + "dev.horizon:pyrite-database or dev.horizon:pyrite-memory"))
        .keyring(vertx, provider);
  }

  /**
   * Creates a key and its first version. The first version is primary at once.
   *
   * <p>
   * The future fails with {@code AlreadyExistsException} if a key has the name, with {@code InvalidArgumentException}
   * if the name is not valid or the rotation and the validity do not fit together, and with
   * {@code UnimplementedException} if the provider does not support the purpose or the algorithm.
   *
   * @param name 1 to 63 letters, digits, {@code -}, {@code _} or {@code .}
   * @param purpose the purpose of the key, which sets its type
   * @param rotation when the key gets a new primary version
   * @param validity how long each version works
   */
  <K extends Key> Future<K> create(String name, Purpose<K> purpose, Rotation rotation, Validity validity);

  /**
   * Returns the encryption key with the name. The future fails with {@code NotFoundException} if no key has the name,
   * and with {@code FailedPreconditionException} and the reason {@link Reason#PURPOSE} if the key has another purpose.
   */
  Future<EncryptionKey> encryption(String name);

  /**
   * Returns the MAC key with the name. The future fails in the same cases as {@link #encryption(String)}.
   */
  Future<MacKey> mac(String name);

  /**
   * Returns the signing key with the name. The future fails in the same cases as {@link #encryption(String)}.
   */
  Future<SigningKey> signing(String name);

  /**
   * Returns a page of keys of every purpose, ordered by name. The request cannot have a {@code filter} or an
   * {@code orderBy}.
   */
  Future<Page<Key>> keys(PageRequest request);

  /**
   * Returns what the provider of this keyring supports.
   */
  Capabilities capabilities();
}
