package dev.horizon.pyrite.internal;

import dev.horizon.pyrite.Key;
import dev.horizon.pyrite.Rotation;
import dev.horizon.pyrite.Version;
import dev.horizon.pyrite.common.NotFoundException;
import dev.horizon.pyrite.common.Revision;
import dev.horizon.pyrite.spi.KeyHandle;
import dev.horizon.pyrite.spi.VersionHandle;
import io.vertx.core.Future;
import java.time.Instant;
import java.util.List;

abstract sealed class ProviderKey permits ProviderEncryptionKey, ProviderMacKey, ProviderSigningKey {

  protected final ProviderKeyring keyring;
  protected final KeyHandle key;
  protected final Lifecycle lifecycle;

  ProviderKey(ProviderKeyring keyring, KeyHandle key, Lifecycle lifecycle) {
    this.keyring = keyring;
    this.key = key;
    this.lifecycle = lifecycle;
  }

  public String name() {
    return key.name();
  }

  public Rotation rotation() {
    return key.rotation();
  }

  public Revision revision() {
    return key.revision();
  }

  public Instant created() {
    return key.created();
  }

  public Future<Version> primary() {
    return lifecycle.settled().map(state -> version(state.primary()
        .orElseThrow(() -> new IllegalStateException("The key " + key.name() + " has no primary version"))));
  }

  public Future<List<Version>> versions() {
    return lifecycle.settled().map(state -> state.versions().stream().map(this::version).toList());
  }

  public Future<Version> version(String id) {
    return lifecycle.settled().map(state -> version(state.version(id)
        .orElseThrow(() -> new NotFoundException("version", key.name() + "/versions/" + id))));
  }

  public Future<Version> rotate() {
    return lifecycle.rotated().map(this::version);
  }

  public Future<Key> rotation(Rotation rotation, Revision expected) {
    return key.rotation(rotation, expected).map(keyring::key);
  }

  Version version(VersionHandle version) {
    return new ProviderVersion(version, lifecycle);
  }
}
