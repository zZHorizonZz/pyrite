package dev.horizon.pyrite.spi;

import dev.horizon.pyrite.Keyring;
import io.vertx.core.Vertx;

/**
 * What {@link Keyring#create} finds through {@link java.util.ServiceLoader}; the artifact {@code dev.horizon:pyrite}
 * provides it.
 */
public interface KeyringFactory {

  /**
   * @return a keyring over {@code provider}
   */
  Keyring keyring(Vertx vertx, Provider provider);
}
