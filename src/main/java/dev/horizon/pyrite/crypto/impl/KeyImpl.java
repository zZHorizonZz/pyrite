package dev.horizon.pyrite.crypto.impl;

import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.impl.resource.BaseResource;
import dev.horizon.pyrite.impl.resource.Resource;
import dev.horizon.pyrite.impl.storage.Row;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

abstract class KeyImpl extends BaseResource {

  static final String ENCRYPTION = "ENCRYPTION";

  KeyImpl(KeyTypeImpl type, Row row) {
    super(type, row);
  }

  @Override
  public KeyTypeImpl type() {
    return (KeyTypeImpl) super.type();
  }

  public String purpose() {
    return field(KeyType.PURPOSE);
  }

  public String primaryVersion() {
    return field(KeyType.PRIMARY_VERSION);
  }

  public Future<List<KeyVersion>> versions() {
    return type().versions().under(name());
  }

  public Future<KeyVersion> version(String version) {
    return type().versions().get(version).map(found -> found.filter(candidate -> candidate.parentName().equals(name()))
        .orElseThrow(() -> new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The key " + name() + " has no"
            + " version " + version + ".")));
  }

  public Future<KeyVersion> primary() {
    Optional<Instant> next = Optional.ofNullable(field(KeyType.NEXT_ROTATION_TIME));
    boolean due = next.map(time -> !time.isAfter(type().schema().clock().instant())).orElse(false);
    return due ? rotate().compose(rotated -> rotated.version(rotated.primaryVersion())) : version(primaryVersion());
  }

  public Future<Key> rotate() {
    return add().compose(this::point);
  }

  @Override
  public Future<Resource> update(Map<String, Object> changes, long revision) {
    Map<String, Object> all = new HashMap<>(checkUpdate(changes, revision));
    if (all.containsKey(KeyType.ROTATION_PERIOD)) {
      long period = all.get(KeyType.ROTATION_PERIOD) instanceof Long given ? given : 0;
      if (period < 0) {
        throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The rotation period is negative.");
      }
      all.put(KeyType.NEXT_ROTATION_TIME, period > 0 ? type().schema().clock().instant().plusSeconds(period) : null);
    }
    return change(all);
  }

  Algorithm algorithm() {
    return Algorithm.valueOf(field(KeyType.ALGORITHM));
  }

  Future<KeyVersion> add() {
    return type().versions().add(this);
  }

  private Future<Key> point(KeyVersion version) {
    long period = field(KeyType.ROTATION_PERIOD);
    Map<String, Object> changes = new HashMap<>();
    changes.put(KeyType.PRIMARY_VERSION, version.name());
    changes.put(KeyType.NEXT_ROTATION_TIME, period > 0 ? type().schema().clock().instant().plusSeconds(period) : null);
    return change(changes).map(Key.class::cast)
        .recover(failure -> failure instanceof PyriteException conflict && conflict.status() == GrpcStatus.ABORTED
            ? type().key(name())
            : Future.failedFuture(failure));
  }
}
