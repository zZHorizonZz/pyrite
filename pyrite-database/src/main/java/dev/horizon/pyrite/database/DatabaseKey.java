package dev.horizon.pyrite.database;

import com.google.protobuf.ByteString;
import dev.horizon.basepoint.Filter;
import dev.horizon.cinder.Assignments;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.Export;
import dev.horizon.pyrite.Origin;
import dev.horizon.pyrite.Purpose;
import dev.horizon.pyrite.Rotation;
import dev.horizon.pyrite.Signing;
import dev.horizon.pyrite.State;
import dev.horizon.pyrite.Validity;
import dev.horizon.pyrite.common.AbortedException;
import dev.horizon.pyrite.common.InvalidArgumentException;
import dev.horizon.pyrite.common.NotFoundException;
import dev.horizon.pyrite.common.Revision;
import dev.horizon.pyrite.common.Violation;
import dev.horizon.pyrite.database.schema.v1.Tables;
import dev.horizon.pyrite.internal.Generator;
import dev.horizon.pyrite.internal.KeyPairMaterial;
import dev.horizon.pyrite.internal.Material;
import dev.horizon.pyrite.internal.PublicKeyMaterial;
import dev.horizon.pyrite.internal.Scheme;
import dev.horizon.pyrite.internal.Vault;
import dev.horizon.pyrite.internal.VersionName;
import dev.horizon.pyrite.spi.KeyHandle;
import dev.horizon.pyrite.spi.VersionHandle;
import io.vertx.core.Future;
import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

final class DatabaseKey implements KeyHandle {

  private static final PurposeMapper PURPOSE_MAPPER = new PurposeMapper();
  private static final RotationMapper ROTATION_MAPPER = new RotationMapper();
  private static final StateMapper STATE_MAPPER = new StateMapper();
  private static final ValidityMapper VALIDITY_MAPPER = new ValidityMapper();
  private static final ExpiryMapper EXPIRY_MAPPER = new ExpiryMapper();
  private static final String ROTATION = "rotation_millis";
  private static final String VALIDITY = "pending_millis,lifetime_millis";

  private final Tables.Key row;
  private final Purpose<?> purpose;
  private final Rotation rotation;
  private final Validity validity;
  private final Database database;
  private final KeyBackend keys;
  private final VersionBackend versions;
  private final Vault vault;
  private final Generator generator;

  DatabaseKey(Tables.Key row, Database database, Vault vault, Generator generator) {
    this.row = row;
    this.purpose = PURPOSE_MAPPER.read(row);
    this.rotation = ROTATION_MAPPER.read(row);
    this.validity = VALIDITY_MAPPER.read(row);
    this.database = database;
    this.keys = new KeyBackend(database);
    this.versions = new VersionBackend(database);
    this.vault = vault;
    this.generator = generator;
  }

  @Override
  public String name() {
    return row.getName();
  }

  @Override
  public Purpose<?> purpose() {
    return purpose;
  }

  @Override
  public Rotation rotation() {
    return rotation;
  }

  @Override
  public Validity validity() {
    return validity;
  }

  @Override
  public Revision revision() {
    return new Revision(row.getRevision());
  }

  @Override
  public Instant created() {
    return Instant.parse(row.getCreateTime());
  }

  @Override
  public Future<List<VersionHandle>> versions() {
    return versions.of(name()).map(rows -> rows.stream().map(this::version).toList());
  }

  @Override
  public Future<KeyHandle> rotation(Rotation rotation, Revision expected) {
    Tables.Key changed = ROTATION_MAPPER.write(row.toBuilder(), rotation).build();
    return changed(expected, () -> keys.update(changed, ROTATION))
        .map(updated -> new DatabaseKey(updated.orElseThrow(() -> new NotFoundException("key", name())), database, vault,
            generator));
  }

  @Override
  public Future<KeyHandle> validity(Validity validity, Revision expected) {
    Tables.Key changed = VALIDITY_MAPPER.write(row.toBuilder(), validity).build();
    return changed(expected, () -> keys.update(changed, VALIDITY))
        .map(updated -> new DatabaseKey(updated.orElseThrow(() -> new NotFoundException("key", name())), database, vault,
            generator));
  }

  @Override
  public Future<VersionHandle> generate(State state, Instant expires, Revision expected) {
    return generator.material(purpose())
        .compose(material -> added(material, Origin.GENERATED, state, expires, expected));
  }

  @Override
  public Future<Export<VersionHandle>> export(State state, Instant expires, Revision expected) {
    return generator.material(purpose()).compose(material -> {
      KeyPairMaterial pair = (KeyPairMaterial) material;
      return added(new PublicKeyMaterial(pair.scheme(), pair.publicKey()), Origin.EXPORTED, state, expires, expected)
          .map(version -> new Export<>(version, pair.pair().getPrivate().getEncoded()));
    });
  }

  @Override
  public Future<VersionHandle> upload(PublicKey publicKey, State state, Instant expires, Revision expected) {
    Scheme scheme = Scheme.valueOf(((Signing) purpose()).algorithm().name());
    if (!scheme.accepts(publicKey)) {
      return Future.failedFuture(new InvalidArgumentException("The public key is not a key of the key's algorithm",
          List.of(new Violation("publicKey", "a " + scheme + " public key"))));
    }
    return added(new PublicKeyMaterial(scheme, publicKey), Origin.UPLOADED, state, expires, expected);
  }

  Future<VersionHandle> added(Material material, Origin origin, State state, Instant expires, Revision expected) {
    return changed(expected, () -> versions.latest(name()).compose(latest -> {
      long number = latest.map(version -> version.getNumber() + 1).orElse(1L);
      Tables.Version.Builder version = EXPIRY_MAPPER.write(STATE_MAPPER.write(Tables.Version.newBuilder()
          .setKeyName(name())
          .setNumber(number)
          .setOrigin(origin.name()), state), expires);
      vault.seal(new VersionName(name(), number), material).map(ByteString::copyFrom).ifPresent(version::setSealed);
      material.subjectPublicKeyInfo().map(ByteString::copyFrom).ifPresent(version::setPublicKey);
      return versions.insert(version.build());
    })).map(this::version);
  }

  Future<VersionHandle> updated(Tables.Version version, String mask, Revision expected) {
    return changed(expected, () -> versions.update(version, mask))
        .map(updated -> version(updated.orElseThrow(() -> new NotFoundException("version",
            new VersionName(name(), version.getNumber()).toString()))));
  }

  private <T> Future<T> changed(Revision expected, Supplier<Future<T>> change) {
    return database.transaction(transaction -> keys.update()
        .where(Filter.and(Filter.eq("name", name()), Filter.eq("revision", expected.value())))
        .set(Assignments.none().increment("revision", 1))
        .execute()
        .compose(advanced -> advanced == 1
            ? change.get()
            : Future.failedFuture(new AbortedException("key", name(), expected))));
  }

  private VersionHandle version(Tables.Version row) {
    return new DatabaseVersion(this, vault, row);
  }
}
