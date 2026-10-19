package dev.horizon.pyrite.memory;

import dev.horizon.pyrite.Export;
import dev.horizon.pyrite.Origin;
import dev.horizon.pyrite.Purpose;
import dev.horizon.pyrite.Rotation;
import dev.horizon.pyrite.Signing;
import dev.horizon.pyrite.State;
import dev.horizon.pyrite.common.InvalidArgumentException;
import dev.horizon.pyrite.common.Revision;
import dev.horizon.pyrite.common.Violation;
import dev.horizon.pyrite.internal.KeyPairMaterial;
import dev.horizon.pyrite.internal.PublicKeyMaterial;
import dev.horizon.pyrite.internal.Scheme;
import dev.horizon.pyrite.spi.KeyHandle;
import dev.horizon.pyrite.spi.VersionHandle;
import io.vertx.core.Future;
import java.security.PublicKey;
import java.time.Instant;
import java.util.List;

record MemoryKey(MemoryEntry entry, String name, Purpose<?> purpose, Rotation rotation, Revision revision,
    Instant created) implements KeyHandle {

  @Override
  public Future<List<VersionHandle>> versions() {
    return Future.succeededFuture(entry.versions());
  }

  @Override
  public Future<KeyHandle> rotation(Rotation rotation, Revision expected) {
    return Future.succeededFuture().map(ignored -> entry.rotation(rotation, expected));
  }

  @Override
  public Future<VersionHandle> generate(State state, Revision expected) {
    return entry.generator().material(purpose).map(material -> entry.added(material, Origin.GENERATED, state,
        expected));
  }

  @Override
  public Future<Export<VersionHandle>> export(State state, Revision expected) {
    return entry.generator().material(purpose).map(material -> {
      KeyPairMaterial pair = (KeyPairMaterial) material;
      MemoryVersion version = entry.added(new PublicKeyMaterial(pair.scheme(), pair.publicKey()), Origin.EXPORTED, state,
          expected);
      return new Export<>(version, pair.pair().getPrivate().getEncoded());
    });
  }

  @Override
  public Future<VersionHandle> upload(PublicKey publicKey, State state, Revision expected) {
    Scheme scheme = Scheme.valueOf(((Signing) purpose).algorithm().name());
    if (!scheme.accepts(publicKey)) {
      return Future.failedFuture(new InvalidArgumentException("The public key is not a key of the key's algorithm",
          List.of(new Violation("publicKey", "a " + scheme + " public key"))));
    }
    return Future.succeededFuture()
        .map(ignored -> entry.added(new PublicKeyMaterial(scheme, publicKey), Origin.UPLOADED, state, expected));
  }
}
