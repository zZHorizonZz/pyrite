package dev.horizon.pyrite.key;

import static dev.horizon.basepoint.expression.Expression.now;

import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import dev.horizon.basepoint.expression.Expression;
import dev.horizon.cinder.Database;
import dev.horizon.cinder.DuplicateKeyException;
import dev.horizon.pyrite.KeyOptions;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.declaration.ResourceType;
import dev.horizon.pyrite.resource.Resource;
import dev.horizon.pyrite.resource.ResourceTree;
import dev.horizon.pyrite.resource.Subject;
import dev.horizon.pyrite.schema.keys.v1.Tables;
import dev.horizon.pyrite.storage.KeyBackend;
import dev.horizon.pyrite.storage.KeyVersionBackend;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The declared type {@code pyrite.dev/Key}: it creates keys and their versions, finds them, and keeps the material of
 * the versions last used, unsealed.
 *
 * <p>
 * A schema whose declaration has {@code pyrite.dev/Key} holds one key type. The material of a version stays in memory
 * after its first use, for the 256 versions last used; the root key unseals any other again when it is used.
 */
public final class KeyType {

  /**
   * How many versions' material stays unsealed in memory; the root key unseals any other again when it is used.
   */
  private static final int MATERIALS = 256;

  /**
   * How many times a new version takes the next number again, when another call took the same number first.
   */
  private static final int NUMBER_ATTEMPTS = 3;

  private final ResourceTree resources;
  private final ResourceType declared;
  private final Vertx vertx;
  private final KeyOptions options;
  private final KeyBackend keys;
  private final KeyVersionBackend versions;
  private final RootKey root;
  private final Map<String, Material> materials = Collections.synchronizedMap(new LinkedHashMap<>(MATERIALS, 0.75f,
      true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, Material> eldest) {
      return size() > MATERIALS;
    }
  });

  public KeyType(ResourceTree resources, ResourceType declared, Database database, Vertx vertx, KeyOptions options,
      RootKey root) {
    this.resources = resources;
    this.declared = declared;
    this.vertx = vertx;
    this.options = options;
    this.keys = new KeyBackend(database);
    this.versions = new KeyVersionBackend(database);
    this.root = root;
  }

  /**
   * Creates a key under a parent, with its first version. When a step after the resource fails, the resource is
   * forgotten again, and the key's rows go with it.
   *
   * @param creator the subject, which needs the type's create permission on the parent, and gets the type's creator
   * relation on the key
   * @param id the key's id, the last segment of its name
   * @param parent the parent's name; empty for a key at the top
   * @param period the rotation period; zero for none
   * @param context the values that the conditions of grants read
   * @return the key with its first version, or a failure with {@code INVALID_ARGUMENT} for a negative period or an id
   * that is not valid, {@code NOT_FOUND} for a parent that is not known, {@code PERMISSION_DENIED} without the create
   * permission, and {@code ALREADY_EXISTS} for a name that is known
   */
  public Future<Key> create(Subject creator, String id, String parent, Algorithm algorithm, Duration period,
      Struct context) {
    if (period.isNegative()) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The rotation period is"
          + " negative."));
    }
    String name = declared.resource(id);
    return resources.create(creator, name, parent, context).compose(resource -> {
      Tables.Key.Builder row = Tables.Key.newBuilder().setName(name).setPurpose(algorithm.purpose())
          .setAlgorithm(Tables.Key.Algorithm.valueOf(algorithm.name())).setRotationPeriod(period.toSeconds())
          .setPrimaryVersion(new KeyVersionName(id, 1).name());
      if (!parent.isEmpty()) {
        row.setParent(parent);
      }
      Map<String, Expression> set = period.isPositive() ? Map.of("next_rotation_time", Expression.add(now(), period))
          : Map.of();
      return keys.insert(row.build(), set).map(this::key).compose(key -> add(key).map(key))
          .recover(failure -> resources.forget(name).transform(ignored -> Future.failedFuture(failure)));
    });
  }

  /**
   * Finds a key.
   *
   * @param name the name, such as {@code keys/payments}
   * @return the key, or a failure with {@code INVALID_ARGUMENT} for a name that is not {@code keys/{key}}, and with
   * {@code NOT_FOUND} for a key that does not exist
   */
  public Future<Key> key(String name) {
    if (!name.startsWith(declared.plural() + "/")) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The name " + name + " is not a"
          + " key's, keys/{key}."));
    }
    return keys.findByName(name).compose(found -> found.isPresent() ? Future.succeededFuture(key(found.get()))
        : Future.failedFuture(new PyriteException(GrpcStatus.NOT_FOUND, "The key " + name + " does not exist.")));
  }

  /**
   * Finds a key that encrypts.
   *
   * @return the key, or a failure with {@code FAILED_PRECONDITION} for a key that signs, and the failures of
   * {@link #key(String)}
   */
  public Future<EncryptionKey> encryption(String name) {
    return key(name).compose(key -> key instanceof EncryptionKey encryption ? Future.succeededFuture(encryption)
        : Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The key " + name + " signs; it"
            + " does not encrypt.")));
  }

  /**
   * Finds a key that signs, of the purpose {@code MAC} or {@code SIGNING}.
   *
   * @return the key, or a failure with {@code FAILED_PRECONDITION} for a key that encrypts, and the failures of
   * {@link #key(String)}
   */
  public Future<SigningKey> signing(String name) {
    return key(name).compose(key -> key instanceof SigningKey signing ? Future.succeededFuture(signing)
        : Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The key " + name + " encrypts;"
            + " it does not sign.")));
  }

  /**
   * Finds a version of a key.
   *
   * @param name the name, such as {@code keyVersions/payments-2}
   * @return the version, or a failure with {@code NOT_FOUND} for a version that does not exist or was destroyed
   */
  public Future<KeyVersion> version(String name) {
    return versions.findByName(name).compose(found -> found.isPresent()
        ? Future.succeededFuture(new KeyVersion(this, found.get()))
        : Future.failedFuture(new PyriteException(GrpcStatus.NOT_FOUND, "The key version " + name + " does not"
            + " exist.")));
  }

  KeyOptions options() {
    return options;
  }

  Resource resource(String name) {
    return new Resource(resources, declared, name);
  }

  KeyBackend keys() {
    return keys;
  }

  KeyVersionBackend versions() {
    return versions;
  }

  Key key(Tables.Key row) {
    return row.getPurpose() == Tables.Key.Purpose.ENCRYPTION ? new EncryptionKey(this, row)
        : new SigningKey(this, row);
  }

  Future<List<KeyVersion>> versions(String key) {
    return versions.children(key).map(rows -> rows.stream().sorted(Comparator.comparingLong(
        Tables.KeyVersion::getNumber)).map(row -> new KeyVersion(this, row)).toList());
  }

  /**
   * Adds a version to a key, numbered after its last one.
   */
  Future<KeyVersion> add(Key key) {
    return add(key, NUMBER_ATTEMPTS);
  }

  /**
   * Gives the material of a version, unsealing it when it is not in memory.
   */
  Material material(KeyVersion version) {
    return materials.computeIfAbsent(version.name(), name -> version.algorithm().restore(root.unseal(
        version.versionName(), version.secretCiphertext()), version.publicKeyInfo()));
  }

  /**
   * Adds a version to a key. Two calls can take the same number; the database keeps one, and the other takes the next
   * number again, up to a number of attempts. The material goes into memory once its row is in the database.
   */
  private Future<KeyVersion> add(Key key, int attempts) {
    return versions(key.name()).compose(found -> {
      KeyVersionName name = new KeyVersionName(key.id(), found.stream().mapToLong(KeyVersion::number).max()
          .orElse(0) + 1);
      Algorithm algorithm = key.algorithm();
      return vertx.executeBlocking(algorithm::generate).compose(material -> {
        Tables.KeyVersion.Builder row = Tables.KeyVersion.newBuilder().setName(name.name()).setParent(key.name())
            .setNumber(name.number()).setState(Tables.KeyVersion.State.ENABLED)
            .setAlgorithm(Tables.Key.Algorithm.valueOf(algorithm.name()))
            .setSealed(ByteString.copyFrom(root.seal(name, material.secret())));
        if (material instanceof Material.Pair pair) {
          row.setPublicKey(algorithm.scheme().jwk(pair.publicKey(), name.id()).encode())
              .setPublicKeyInfo(ByteString.copyFrom(pair.publicKey().getEncoded()));
        }
        return versions.insert(row.build()).map(inserted -> {
          materials.put(name.name(), material);
          return new KeyVersion(this, inserted);
        });
      });
    }).recover(failure -> failure instanceof DuplicateKeyException && attempts > 1 ? add(key, attempts - 1)
        : Future.failedFuture(failure));
  }
}
