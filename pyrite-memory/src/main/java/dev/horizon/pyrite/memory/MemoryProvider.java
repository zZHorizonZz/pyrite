package dev.horizon.pyrite.memory;

import dev.horizon.aquery.aip158.Page;
import dev.horizon.aquery.aip158.PageRequest;
import dev.horizon.pyrite.Algorithm;
import dev.horizon.pyrite.Capabilities;
import dev.horizon.pyrite.Capability;
import dev.horizon.pyrite.Purpose;
import dev.horizon.pyrite.Rotation;
import dev.horizon.pyrite.State;
import dev.horizon.pyrite.Validity;
import dev.horizon.pyrite.common.AlreadyExistsException;
import dev.horizon.pyrite.common.InvalidArgumentException;
import dev.horizon.pyrite.common.NotFoundException;
import dev.horizon.pyrite.common.Violation;
import dev.horizon.pyrite.internal.AesMaterial;
import dev.horizon.pyrite.internal.Generator;
import dev.horizon.pyrite.internal.Vault;
import dev.horizon.pyrite.spi.KeyHandle;
import dev.horizon.pyrite.spi.Provider;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

public final class MemoryProvider implements Provider {

  private static final int PAGE_SIZE = 50;
  private static final int MAXIMUM_PAGE_SIZE = 1000;
  private static final Capabilities CAPABILITIES = new Capabilities(EnumSet.allOf(Capability.class),
      EnumSet.allOf(Algorithm.class));
  private static final SecureRandom RANDOM = new SecureRandom();

  private final Generator generator;
  private final Vault vault;
  private final Clock clock;
  private final NavigableMap<String, MemoryEntry> keys = new ConcurrentSkipListMap<>();

  public MemoryProvider(Vertx vertx) {
    this(vertx, Clock.systemUTC());
  }

  public MemoryProvider(Vertx vertx, Clock clock) {
    byte[] vaultKey = new byte[AesMaterial.KEY_BYTES];
    RANDOM.nextBytes(vaultKey);
    this.generator = new Generator(vertx);
    this.vault = new Vault(new AesMaterial(vaultKey));
    this.clock = clock;
  }

  @Override
  public String name() {
    return "memory";
  }

  @Override
  public Capabilities capabilities() {
    return CAPABILITIES;
  }

  @Override
  public Future<KeyHandle> create(String name, Purpose<?> purpose, Rotation rotation, Validity validity, State first,
      Instant expires) {
    return generator.material(purpose).map(material -> {
      MemoryEntry entry = new MemoryEntry(generator, vault, clock, name, purpose, rotation, validity, material,
          first, expires);
      if (keys.putIfAbsent(name, entry) != null) {
        throw new AlreadyExistsException("key", name);
      }
      return entry.key();
    });
  }

  @Override
  public Future<KeyHandle> key(String name) {
    MemoryEntry entry = keys.get(name);
    return entry == null ? Future.failedFuture(new NotFoundException("key", name)) : Future.succeededFuture(entry.key());
  }

  @Override
  public Future<Page<KeyHandle>> keys(PageRequest request) {
    String after;
    try {
      after = request.pageToken() == null || request.pageToken().isEmpty()
          ? ""
          : new String(Base64.getUrlDecoder().decode(request.pageToken()), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException malformed) {
      return Future.failedFuture(new InvalidArgumentException("The page token is not one this provider made",
          List.of(new Violation("pageToken", "a token from the previous page"))));
    }
    int size = request.pageSize() <= 0 ? PAGE_SIZE : Math.min(request.pageSize(), MAXIMUM_PAGE_SIZE);
    List<MemoryEntry> following = keys.tailMap(after, false).values().stream().limit(size + 1L).toList();
    List<KeyHandle> page = following.stream().limit(size).map(MemoryEntry::key).map(KeyHandle.class::cast).toList();
    String next = following.size() > size
        ? Base64.getUrlEncoder().withoutPadding().encodeToString(page.getLast().name().getBytes(StandardCharsets.UTF_8))
        : "";
    return Future.succeededFuture(new Page<>(page, next, keys.size()));
  }
}
