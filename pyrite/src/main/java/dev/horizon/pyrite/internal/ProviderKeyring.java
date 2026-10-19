package dev.horizon.pyrite.internal;

import dev.horizon.aquery.aip158.Page;
import dev.horizon.aquery.aip158.PageRequest;
import dev.horizon.pyrite.Capabilities;
import dev.horizon.pyrite.Capability;
import dev.horizon.pyrite.Encryption;
import dev.horizon.pyrite.EncryptionKey;
import dev.horizon.pyrite.Key;
import dev.horizon.pyrite.Keyring;
import dev.horizon.pyrite.Mac;
import dev.horizon.pyrite.MacKey;
import dev.horizon.pyrite.Purpose;
import dev.horizon.pyrite.Reason;
import dev.horizon.pyrite.Rotation;
import dev.horizon.pyrite.Signing;
import dev.horizon.pyrite.SigningKey;
import dev.horizon.pyrite.State;
import dev.horizon.pyrite.common.FailedPreconditionException;
import dev.horizon.pyrite.common.InvalidArgumentException;
import dev.horizon.pyrite.common.UnimplementedException;
import dev.horizon.pyrite.common.Violation;
import dev.horizon.pyrite.spi.KeyHandle;
import dev.horizon.pyrite.spi.Provider;
import dev.horizon.pyrite.spi.VersionHandle;
import io.vertx.core.Future;
import java.security.PublicKey;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class ProviderKeyring implements Keyring {

  private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._-]{1,63}");

  private final Provider provider;
  private final Clock clock;
  private final Certificates certificates = new Certificates();
  private final Map<String, PublicKey> publicKeys = new ConcurrentHashMap<>();

  public ProviderKeyring(Provider provider, Clock clock) {
    this.provider = provider;
    this.clock = clock;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <K extends Key> Future<K> create(String name, Purpose<K> purpose, Rotation rotation) {
    if (!NAME.matcher(name).matches()) {
      return Future.failedFuture(new InvalidArgumentException("The name of a key is not valid",
          List.of(new Violation("name", "letters, digits, '-', '_' and '.', from 1 to 63 of them"))));
    }
    String unsupported = switch (purpose) {
      case Encryption encryption -> provider.capabilities().has(Capability.ENCRYPTION) ? "" : "encryption keys";
      case Mac mac -> provider.capabilities().has(Capability.MAC) ? "" : "MAC keys";
      case Signing signing -> provider.capabilities().has(Capability.SIGNING)
          && provider.capabilities().signs(signing.algorithm()) ? "" : signing.algorithm() + " signing keys";
    };
    if (!unsupported.isEmpty()) {
      return Future.failedFuture(unimplemented("hold " + unsupported));
    }
    return provider.create(name, purpose, rotation, new State.Primary(clock.instant()))
        .compose(created -> lifecycle(name).settled())
        .map(state -> (K) key(state.key()));
  }

  @Override
  public Future<EncryptionKey> encryption(String name) {
    return provider.key(name).map(key -> switch (key(key)) {
      case EncryptionKey encryption -> encryption;
      default -> throw purpose(name, "an encryption key");
    });
  }

  @Override
  public Future<MacKey> mac(String name) {
    return provider.key(name).map(key -> switch (key(key)) {
      case MacKey mac -> mac;
      default -> throw purpose(name, "a MAC key");
    });
  }

  @Override
  public Future<SigningKey> signing(String name) {
    return provider.key(name).map(key -> switch (key(key)) {
      case SigningKey signing -> signing;
      default -> throw purpose(name, "a signing key");
    });
  }

  @Override
  public Future<Page<Key>> keys(PageRequest request) {
    return provider.keys(request)
        .map(page -> new Page<>(page.items().stream().map(this::key).toList(), page.nextPageToken(), page.totalSize()));
  }

  @Override
  public Capabilities capabilities() {
    return provider.capabilities();
  }

  Key key(KeyHandle key) {
    Lifecycle lifecycle = lifecycle(key.name());
    return switch (key.purpose()) {
      case Encryption encryption -> new ProviderEncryptionKey(this, key, lifecycle);
      case Mac mac -> new ProviderMacKey(this, key, lifecycle);
      case Signing signing -> new ProviderSigningKey(this, key, lifecycle);
    };
  }

  UnimplementedException unimplemented(String what) {
    return new UnimplementedException("The provider " + provider.name() + " does not " + what);
  }

  Future<PublicKey> publicKey(String key, VersionHandle version) {
    PublicKey held = publicKeys.get(key + "/" + version.id());
    if (held != null) {
      return Future.succeededFuture(held);
    }
    return version.publicKey().onSuccess(found -> publicKeys.put(key + "/" + version.id(), found));
  }

  private Lifecycle lifecycle(String name) {
    return new Lifecycle(provider, name, clock, certificates);
  }

  private FailedPreconditionException purpose(String name, String asked) {
    return new FailedPreconditionException(Reason.PURPOSE.name(), "The key " + name + " is not " + asked);
  }
}
