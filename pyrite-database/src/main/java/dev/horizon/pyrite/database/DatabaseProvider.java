package dev.horizon.pyrite.database;

import com.google.crypto.tink.Aead;
import dev.horizon.aquery.aip158.Page;
import dev.horizon.aquery.aip158.PageRequest;
import dev.horizon.cinder.Database;
import dev.horizon.cinder.Driver;
import dev.horizon.cinder.DuplicateKeyException;
import dev.horizon.cinder.protobuf.ProtobufExtension;
import dev.horizon.pyrite.Algorithm;
import dev.horizon.pyrite.Capabilities;
import dev.horizon.pyrite.Capability;
import dev.horizon.pyrite.Origin;
import dev.horizon.pyrite.Purpose;
import dev.horizon.pyrite.Rotation;
import dev.horizon.pyrite.State;
import dev.horizon.pyrite.Validity;
import dev.horizon.pyrite.common.AlreadyExistsException;
import dev.horizon.pyrite.common.NotFoundException;
import dev.horizon.pyrite.common.Revision;
import dev.horizon.pyrite.database.schema.v1.Tables;
import dev.horizon.pyrite.internal.Generator;
import dev.horizon.pyrite.internal.Vault;
import dev.horizon.pyrite.spi.KeyHandle;
import dev.horizon.pyrite.spi.Provider;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;
import java.time.Instant;
import java.util.EnumSet;

public final class DatabaseProvider implements Provider {

  private static final PurposeMapper PURPOSE_MAPPER = new PurposeMapper();
  private static final RotationMapper ROTATION_MAPPER = new RotationMapper();
  private static final ValidityMapper VALIDITY_MAPPER = new ValidityMapper();
  private static final Capabilities CAPABILITIES = new Capabilities(EnumSet.allOf(Capability.class),
      EnumSet.allOf(Algorithm.class));

  private final Database database;
  private final KeyTable keys;
  private final VaultKey vaultKey;
  private final Vault vault;
  private final Generator generator;

  private DatabaseProvider(Database database, VaultKey vaultKey, Vault vault, Generator generator) {
    this.database = database;
    this.keys = new KeyTable(database);
    this.vaultKey = vaultKey;
    this.vault = vault;
    this.generator = generator;
  }

  public static Future<DatabaseProvider> open(Vertx vertx, Pool pool, Driver driver, Aead root) {
    Database database = Database.builder(pool, driver).extension(new ProtobufExtension()).build();
    VaultKey vaultKey = new VaultKey(new VaultTable(database), new VersionTable(database), root);
    return vaultKey.load()
        .map(key -> new DatabaseProvider(database, vaultKey, new Vault(key), new Generator(vertx)));
  }

  @Override
  public String name() {
    return "database";
  }

  @Override
  public Capabilities capabilities() {
    return CAPABILITIES;
  }

  @Override
  public Future<KeyHandle> create(String name, Purpose<?> purpose, Rotation rotation, Validity validity, State first,
      Instant expires) {
    Tables.Key row = VALIDITY_MAPPER.write(ROTATION_MAPPER.write(PURPOSE_MAPPER.write(Tables.Key.newBuilder()
        .setName(name)
        .setRevision(0), purpose), rotation), validity).build();
    return generator.material(purpose)
        .compose(material -> database.transaction(transaction -> keys.insert(row)
            .compose(inserted -> key(inserted).added(material, Origin.GENERATED, first, expires, new Revision(0)))))
        .recover(failure -> Future.failedFuture(failure instanceof DuplicateKeyException
            ? new AlreadyExistsException("key", name)
            : failure))
        .compose(added -> key(name));
  }

  @Override
  public Future<KeyHandle> key(String name) {
    return keys.findById(name)
        .<KeyHandle> map(found -> key(found.orElseThrow(() -> new NotFoundException("key", name))));
  }

  @Override
  public Future<Page<KeyHandle>> keys(PageRequest request) {
    return keys.list(new PageRequest("", "name", request.pageSize(), request.pageToken()))
        .map(page -> new Page<>(page.items().stream().<KeyHandle> map(this::key).toList(), page.nextPageToken(),
            page.totalSize()));
  }

  public Future<Void> rewrap() {
    return vaultKey.rewrap();
  }

  private DatabaseKey key(Tables.Key row) {
    return new DatabaseKey(row, database, vault, generator);
  }
}
