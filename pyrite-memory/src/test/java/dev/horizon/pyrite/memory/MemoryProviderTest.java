package dev.horizon.pyrite.memory;

import dev.horizon.pyrite.contract.KeyringContract;
import dev.horizon.pyrite.spi.Provider;
import io.vertx.core.Vertx;
import java.time.Clock;

class MemoryProviderTest extends KeyringContract {

  @Override
  protected Provider provider(Vertx vertx, Clock clock) {
    return new MemoryProvider(vertx, clock);
  }
}
