package dev.horizon.pyrite.crypto.impl;

import com.google.crypto.tink.Aead;
import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.impl.model.DeclaredType;
import dev.horizon.pyrite.impl.resource.Resource;
import dev.horizon.pyrite.impl.resource.Schema;
import dev.horizon.pyrite.impl.resource.Subject;
import dev.horizon.pyrite.impl.storage.KeyBackend;
import dev.horizon.pyrite.impl.storage.KeyVersionBackend;
import dev.horizon.pyrite.schema.keys.v1.Tables;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class KeyTypeImpl {

  static final String VERSIONS = "keyVersions/";

  private final Schema schema;
  private final DeclaredType declared;
  private final KeyBackend keys;
  private final KeyVersionBackend versions;
  private final Aead root;
  private final Map<String, Material> unsealed = new ConcurrentHashMap<>();

  public KeyTypeImpl(Schema schema, DeclaredType declared, Database database, Aead root) {
    this.schema = schema;
    this.declared = declared;
    this.keys = new KeyBackend(database);
    this.versions = new KeyVersionBackend(database);
    this.root = root;
  }

  public Future<Key> create(Subject creator, String id, String parent, Algorithm algorithm, Duration period,
      Struct context) {
    if (period.isNegative()) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The rotation period is negative.");
    }
    String name = declared.resource(id);
    return schema.create(creator, name, parent, context).compose(resource -> {
      Instant now = schema.clock().instant();
      Tables.Key.Builder row = Tables.Key.newBuilder().setUid(UUID.randomUUID().toString()).setName(name)
          .setLabels("{}").setRevision(1).setCreateTime(now.toString()).setUpdateTime(now.toString())
          .setPurpose(algorithm.purpose()).setAlgorithm(algorithm.name()).setRotationPeriod(period.toSeconds())
          .setPrimaryVersion(VERSIONS + id + "-1");
      if (!parent.isEmpty()) {
        row.setParent(parent);
      }
      if (period.isPositive()) {
        row.setNextRotationTime(now.plus(period).toString());
      }
      Key key = key(row.build());
      return keys.insert(row.build()).compose(ignored -> add((KeyImpl) key)).map(key);
    });
  }

  public Future<Key> key(String name) {
    if (!name.startsWith(declared.plural() + "/")) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The name " + name + " is not a key's, keys/{key}.");
    }
    return keys.findByName(name).map(found -> key(found.orElseThrow(() -> new PyriteException(GrpcStatus.NOT_FOUND,
        "The key " + name + " does not exist."))));
  }

  public Future<EncryptionKey> encryption(String name) {
    return key(name).map(key -> {
      if (!(key instanceof EncryptionKey encryption)) {
        throw new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The key " + name + " signs; it does not"
            + " encrypt.");
      }
      return encryption;
    });
  }

  public Future<SigningKey> signing(String name) {
    return key(name).map(key -> {
      if (!(key instanceof SigningKey signing)) {
        throw new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The key " + name + " encrypts; it does not"
            + " sign.");
      }
      return signing;
    });
  }

  public Future<KeyVersion> version(String name) {
    return versions.findByName(name).map(found -> new KeyVersionImpl(this, found.orElseThrow(() -> new PyriteException(
        GrpcStatus.NOT_FOUND, "The key version " + name + " does not exist."))));
  }

  Schema schema() {
    return schema;
  }

  Resource resource(String name) {
    return new Resource(schema, declared, name);
  }

  KeyBackend keys() {
    return keys;
  }

  KeyVersionBackend versions() {
    return versions;
  }

  Key key(Tables.Key row) {
    return KeyImpl.ENCRYPTION.equals(row.getPurpose()) ? new EncryptionKeyImpl(this, row)
        : new SigningKeyImpl(this, row);
  }

  Future<List<KeyVersion>> versions(String key) {
    return versions.children(key).map(rows -> rows.stream().sorted(Comparator.comparingLong(Tables.KeyVersion::getNumber))
        .<KeyVersion> map(row -> new KeyVersionImpl(this, row)).toList());
  }

  Future<KeyVersion> add(KeyImpl key) {
    return versions(key.name()).compose(found -> {
      long number = found.stream().mapToLong(KeyVersion::number).max().orElse(0) + 1;
      String id = key.id() + "-" + number;
      String name = VERSIONS + id;
      Algorithm algorithm = key.algorithm();
      return schema.vertx().executeBlocking(() -> Material.generate(algorithm)).compose(material -> {
        Instant now = schema.clock().instant();
        Tables.KeyVersion.Builder row = Tables.KeyVersion.newBuilder().setUid(UUID.randomUUID().toString())
            .setName(name).setParent(key.name()).setLabels("{}").setRevision(1).setCreateTime(now.toString())
            .setUpdateTime(now.toString()).setNumber(number).setState(KeyVersionImpl.ENABLED)
            .setAlgorithm(algorithm.name());
        if (material instanceof Material.Pair pair) {
          row.setPublicKey(algorithm.scheme().jwk(pair.publicKey(), id).encode())
              .setPublicKeyInfo(ByteString.copyFrom(pair.publicKey().getEncoded()));
        }
        try {
          row.setSealed(ByteString.copyFrom(root.encrypt(material.secret(),
              associatedData(name))));
        } catch (GeneralSecurityException refused) {
          throw new IllegalStateException("The root key did not encrypt the version " + name, refused);
        }
        unsealed.put(name, material);
        return versions.insert(row.build()).map(ignored -> (KeyVersion) new KeyVersionImpl(this, row.build()));
      });
    });
  }

  Material material(KeyVersionImpl version) {
    return unsealed.computeIfAbsent(version.name(), name -> {
      try {
        return Material.restore(version.algorithm(), root.decrypt(version.sealedSecret(), associatedData(name)),
            version.publicKeyInfo());
      } catch (GeneralSecurityException unreadable) {
        throw new IllegalStateException("The root key does not decrypt the version " + name, unreadable);
      }
    });
  }

  private static byte[] associatedData(String version) {
    byte[] prefix = "pyrite\0".getBytes(StandardCharsets.UTF_8);
    byte[] name = version.getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(prefix.length + name.length).put(prefix).put(name).array();
  }
}
