package dev.horizon.pyrite.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.horizon.pyrite.Purpose;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

public final class Vault {

  private static final int HELD = 10_000;
  private static final Duration IDLE = Duration.ofHours(1);

  private final AesMaterial key;
  private final Cache<VersionName, Material> unsealed = Caffeine.newBuilder()
      .maximumSize(HELD)
      .expireAfterAccess(IDLE)
      .build();

  public Vault(AesMaterial key) {
    this.key = key;
  }

  public Optional<byte[]> seal(VersionName name, Material material) {
    return material.secret().map(secret -> key.encrypt(secret, associatedData(name)));
  }

  public Material unseal(VersionName name, Purpose<?> purpose, byte[] sealed, byte[] subjectPublicKeyInfo) {
    return unsealed.get(name, held -> Material.of(purpose,
        sealed.length == 0 ? sealed : key.decrypt(sealed, associatedData(name)), subjectPublicKeyInfo));
  }

  public void forget(VersionName name) {
    unsealed.invalidate(name);
  }

  private byte[] associatedData(VersionName name) {
    return ("pyrite" + '\0' + name.key() + '\0' + name.number()).getBytes(StandardCharsets.UTF_8);
  }
}
