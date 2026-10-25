package dev.horizon.pyrite.crypto.impl;

import com.google.protobuf.Timestamp;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.impl.resource.Resource;
import dev.horizon.pyrite.schema.keys.v1.Tables;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

abstract class KeyImpl {

  static final String ENCRYPTION = "ENCRYPTION";

  private final KeyTypeImpl type;
  private final Tables.Key row;

  KeyImpl(KeyTypeImpl type, Tables.Key row) {
    this.type = type;
    this.row = row;
  }

  public String name() {
    return row.getName();
  }

  public Resource resource() {
    return type.resource(name());
  }

  public String primaryVersion() {
    return row.getPrimaryVersion();
  }

  public Future<List<KeyVersion>> versions() {
    return type.versions(name());
  }

  public Future<KeyVersion> primary() {
    boolean due = row.hasNextRotationTime()
        && !Instant.parse(row.getNextRotationTime()).isAfter(type.schema().clock().instant());
    return due ? rotate().compose(rotated -> type.version(rotated.primaryVersion())) : type.version(primaryVersion());
  }

  public Future<Key> rotate() {
    return type.add(this).compose(this::point);
  }

  public Future<Key> update(Duration period, long revision) {
    if (period.isNegative()) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The rotation period is negative.");
    }
    if (row.getRevision() != revision) {
      throw new PyriteException(GrpcStatus.ABORTED, "The key " + name() + " is at revision " + row.getRevision()
          + ", not " + revision + ".");
    }
    Tables.Key.Builder changed = row.toBuilder().setRotationPeriod(period.toSeconds()).clearNextRotationTime();
    if (period.isPositive()) {
      changed.setNextRotationTime(type.schema().clock().instant().plus(period).toString());
    }
    return save(changed);
  }

  public dev.horizon.pyrite.crypto.v1.Key message() {
    dev.horizon.pyrite.crypto.v1.Key.Builder message = dev.horizon.pyrite.crypto.v1.Key.newBuilder()
        .setName(name()).setParent(row.getParent())
        .setPurpose(dev.horizon.pyrite.crypto.v1.Key.Purpose.valueOf(row.getPurpose()))
        .setAlgorithm(dev.horizon.pyrite.crypto.v1.Algorithm.valueOf(row.getAlgorithm()))
        .setRotationPeriod(com.google.protobuf.Duration.newBuilder().setSeconds(row.getRotationPeriod()))
        .setPrimaryVersion(primaryVersion()).setRevision(row.getRevision())
        .setCreateTime(timestamp(row.getCreateTime())).setUpdateTime(timestamp(row.getUpdateTime()));
    if (row.hasNextRotationTime()) {
      message.setNextRotationTime(timestamp(row.getNextRotationTime()));
    }
    return message.build();
  }

  KeyTypeImpl type() {
    return type;
  }

  String id() {
    return name().substring(name().indexOf('/') + 1);
  }

  Algorithm algorithm() {
    return Algorithm.valueOf(row.getAlgorithm());
  }

  static Timestamp timestamp(String time) {
    Instant instant = Instant.parse(time);
    return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
  }

  private Future<Key> point(KeyVersion version) {
    Tables.Key.Builder changed = row.toBuilder().setPrimaryVersion(version.name()).clearNextRotationTime();
    if (row.getRotationPeriod() > 0) {
      changed.setNextRotationTime(type.schema().clock().instant().plusSeconds(row.getRotationPeriod()).toString());
    }
    return save(changed).recover(failure -> failure instanceof PyriteException conflict
        && conflict.status() == GrpcStatus.ABORTED ? type.key(name()) : Future.failedFuture(failure));
  }

  private Future<Key> save(Tables.Key.Builder changed) {
    return type.keys().update(changed.setRevision(row.getRevision() + 1)
        .setUpdateTime(type.schema().clock().instant().toString()).build(), row.getUid(), row.getRevision(), name())
        .map(type::key);
  }
}
