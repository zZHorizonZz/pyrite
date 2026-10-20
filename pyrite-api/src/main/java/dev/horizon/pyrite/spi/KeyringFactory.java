package dev.horizon.pyrite.spi;

import dev.horizon.pyrite.Keyring;
import io.vertx.core.Vertx;

/**
 * Creates keyrings for {@link Keyring#create}, which finds it with {@link java.util.ServiceLoader}. The artifact
 * {@code dev.horizon:pyrite-internal} implements it.
 */
public interface KeyringFactory {

  Keyring keyring(Vertx vertx, Provider provider);
}
