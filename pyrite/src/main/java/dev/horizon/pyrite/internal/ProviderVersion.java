package dev.horizon.pyrite.internal;

import dev.horizon.pyrite.Origin;
import dev.horizon.pyrite.State;
import dev.horizon.pyrite.Version;
import dev.horizon.pyrite.spi.VersionHandle;
import io.vertx.core.Future;
import java.time.Instant;

final class ProviderVersion implements Version {

  private final VersionHandle version;
  private final Lifecycle lifecycle;

  ProviderVersion(VersionHandle version, Lifecycle lifecycle) {
    this.version = version;
    this.lifecycle = lifecycle;
  }

  @Override
  public String id() {
    return version.id();
  }

  @Override
  public State state() {
    return version.state();
  }

  @Override
  public Origin origin() {
    return version.origin();
  }

  @Override
  public Instant created() {
    return version.created();
  }

  @Override
  public Future<Version> disable() {
    return lifecycle.disabled(version.id()).map(changed -> new ProviderVersion(changed, lifecycle));
  }

  @Override
  public Future<Version> enable() {
    return lifecycle.enabled(version.id()).map(changed -> new ProviderVersion(changed, lifecycle));
  }

  @Override
  public Future<Version> destroy() {
    return lifecycle.destroyed(version.id()).map(changed -> new ProviderVersion(changed, lifecycle));
  }

  @Override
  public String toString() {
    return "Version[" + lifecycle.name() + "/versions/" + version.id() + ", " + version.state() + "]";
  }
}
