package dev.horizon.pyrite.internal;

import dev.horizon.pyrite.Keyring;
import dev.horizon.pyrite.spi.KeyringFactory;
import dev.horizon.pyrite.spi.Provider;
import io.vertx.core.Vertx;
import java.time.Clock;

public final class ProviderKeyringFactory implements KeyringFactory {

  @Override
  public Keyring keyring(Vertx vertx, Provider provider) {
    return new ProviderKeyring(provider, Clock.systemUTC());
  }
}
