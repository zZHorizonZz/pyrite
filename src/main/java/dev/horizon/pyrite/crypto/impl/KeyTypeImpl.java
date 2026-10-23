package dev.horizon.pyrite.crypto.impl;

import dev.horizon.cinder.Database;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.impl.model.DeclaredType;
import dev.horizon.pyrite.impl.model.TypeSystem;
import dev.horizon.pyrite.impl.resource.BaseResourceType;
import dev.horizon.pyrite.impl.resource.Resource;
import dev.horizon.pyrite.impl.resource.SchemaImpl;
import dev.horizon.pyrite.impl.storage.Row;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class KeyTypeImpl extends BaseResourceType<Key> implements KeyType {

  public KeyTypeImpl(DeclaredType declared, SchemaImpl schema, Database database) {
    super(declared, schema, database, Set.of(PRIMARY_VERSION, NEXT_ROTATION_TIME), Set.of(),
        Set.of(PURPOSE, ALGORITHM), false);
  }

  @Override
  protected Key resource(Row row) {
    return KeyImpl.ENCRYPTION.equals(row.get(PURPOSE)) ? new EncryptionKeyImpl(this, row)
        : new SigningKeyImpl(this, row);
  }

  @Override
  protected Future<Key> insert(Optional<Resource> parent, String id, Map<String, Object> values) {
    if (!(values.get(PURPOSE) instanceof String purpose) || !(values.get(ALGORITHM) instanceof String named)) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "A key names its purpose and its algorithm.");
    }
    Algorithm algorithm = Arrays.stream(Algorithm.values()).filter(known -> known.name().equals(named)).findFirst()
        .orElseThrow(() -> new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The algorithm " + named + " is not"
            + " one of " + Arrays.toString(Algorithm.values()) + "."));
    if (!algorithm.purpose().equals(purpose)) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The algorithm " + named + " is for "
          + algorithm.purpose() + " keys, not " + purpose + " keys.");
    }
    long period = values.get(ROTATION_PERIOD) instanceof Long given ? given : 0;
    if (period < 0) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The rotation period is negative.");
    }
    Map<String, Object> own = new HashMap<>(values);
    own.put(ROTATION_PERIOD, period);
    own.put(PRIMARY_VERSION, versions().declaration().resource(id + "-1"));
    own.put(NEXT_ROTATION_TIME, period > 0 ? schema().clock().instant().plusSeconds(period) : null);
    return super.insert(parent, id, own).compose(key -> ((KeyImpl) key).add().map(key));
  }

  @Override
  public Future<Key> key(String name) {
    return schema().require(name).map(found -> {
      if (!(found instanceof Key key)) {
        throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The resource " + name + " is not a key.");
      }
      if (key.isDeleted()) {
        throw new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The key " + name + " is deleted.");
      }
      return key;
    });
  }

  @Override
  public Future<EncryptionKey> encryption(String name) {
    return key(name).map(key -> {
      if (!(key instanceof EncryptionKey encryption)) {
        throw new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The key " + name + " is a " + key.purpose()
            + " key; it does not encrypt.");
      }
      return encryption;
    });
  }

  @Override
  public Future<SigningKey> signing(String name) {
    return key(name).map(key -> {
      if (!(key instanceof SigningKey signing)) {
        throw new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The key " + name + " encrypts; it does not"
            + " sign.");
      }
      return signing;
    });
  }

  KeyVersionTypeImpl versions() {
    return schema().type(TypeSystem.KEY_VERSION, KeyVersionTypeImpl.class);
  }
}
