package dev.horizon.pyrite.crypto.impl;

import com.google.crypto.tink.Aead;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.impl.model.DeclaredType;
import dev.horizon.pyrite.impl.resource.BaseResourceType;
import dev.horizon.pyrite.impl.resource.SchemaImpl;
import dev.horizon.pyrite.impl.resource.Subject;
import dev.horizon.pyrite.impl.storage.Row;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.GrpcStatus;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class KeyVersionTypeImpl extends BaseResourceType<KeyVersion> implements KeyVersionType {

  private final Aead root;
  private final Map<String, Material> unsealed = new ConcurrentHashMap<>();

  public KeyVersionTypeImpl(DeclaredType declared, SchemaImpl schema, Database database, Aead root) {
    super(declared, schema, database, Set.of(NUMBER, ALGORITHM, PUBLIC_KEY, SEALED, PUBLIC_KEY_INFO),
        Set.of(SEALED, PUBLIC_KEY_INFO), Set.of(), true);
    this.root = root;
  }

  @Override
  protected KeyVersion resource(Row row) {
    return new KeyVersionImpl(this, row);
  }

  @Override
  public Future<KeyVersion> create(Subject creator, String parent, String id, Map<String, Object> values) {
    if (!id.isEmpty() || !values.isEmpty()) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "Pyrite names and fills the versions of a key; the"
          + " request names no id and sets no fields.");
    }
    return place(parent)
        .compose(key -> key.orElseThrow().require(creator, permissions().getCreate()))
        .compose(key -> ((Key) key).rotate())
        .compose(key -> key.version(key.primaryVersion()));
  }

  Future<KeyVersion> add(KeyImpl key) {
    return key.versions().compose(found -> {
      long number = found.stream().mapToLong(KeyVersion::number).max().orElse(0) + 1;
      String id = key.id() + "-" + number;
      String name = declaration().resource(id);
      Algorithm algorithm = key.algorithm();
      return schema().vertx().executeBlocking(() -> Material.generate(algorithm)).compose(material -> {
        Map<String, Object> values = new HashMap<>();
        values.put(NUMBER, number);
        values.put(STATE, KeyVersionImpl.ENABLED);
        values.put(ALGORITHM, algorithm.name());
        if (material instanceof Material.Pair pair) {
          values.put(PUBLIC_KEY, algorithm.scheme().jwk(pair.publicKey(), id));
          values.put(PUBLIC_KEY_INFO, pair.publicKey().getEncoded());
        } else {
          values.put(PUBLIC_KEY, new JsonObject());
          values.put(PUBLIC_KEY_INFO, new byte[0]);
        }
        try {
          values.put(SEALED, root.encrypt(material.secret(), associatedData(name)));
        } catch (GeneralSecurityException refused) {
          throw new IllegalStateException("The root key did not encrypt the version " + name, refused);
        }
        unsealed.put(name, material);
        return insert(Optional.of(key), id, values);
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
