package dev.horizon.pyrite.internal;

import dev.horizon.pyrite.Mac;
import dev.horizon.pyrite.MacKey;
import dev.horizon.pyrite.spi.KeyHandle;
import dev.horizon.pyrite.spi.VersionHandle;
import io.vertx.core.Future;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

final class ProviderMacKey extends ProviderKey implements MacKey {

  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

  ProviderMacKey(ProviderKeyring keyring, KeyHandle key, Lifecycle lifecycle) {
    super(keyring, key, lifecycle);
  }

  @Override
  public Mac purpose() {
    return (Mac) key.purpose();
  }

  @Override
  public Future<String> fingerprint(String text) {
    return lifecycle.settled().compose(state -> fingerprint(state.current(), text));
  }

  @Override
  public Future<List<String>> fingerprints(String text) {
    return lifecycle.settled().compose(state -> Future.all(state.live().stream()
        .map(version -> fingerprint(version, text))
        .toList()))
        .map(all -> all.<String> list());
  }

  private Future<String> fingerprint(VersionHandle version, String text) {
    return version.mac((key.name() + '\0' + text).getBytes(StandardCharsets.UTF_8))
        .map(tag -> version.id() + "." + ENCODER.encodeToString(tag));
  }
}
