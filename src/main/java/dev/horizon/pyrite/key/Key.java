package dev.horizon.pyrite.key;

import static dev.horizon.basepoint.expression.Expression.add;
import static dev.horizon.basepoint.expression.Expression.now;

import dev.horizon.basepoint.expression.Expression;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.resource.Resource;
import dev.horizon.pyrite.schema.keys.v1.Tables;
import dev.horizon.pyrite.storage.DatabaseTime;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A key, the resource {@code pyrite.dev/Key}: an encryption key or a key that signs. The primary version encrypts and
 * signs; every enabled version decrypts and verifies.
 *
 * <p>
 * A key holds the row that it was read from: a change gives a new key, and leaves this one as it was read. A key with a
 * rotation period rotates at its first encryption or signature after its next rotation time, on the clock of the
 * database.
 */
public abstract sealed class Key permits EncryptionKey, SigningKey {

  private final KeyType type;
  private final Tables.Key row;

  Key(KeyType type, Tables.Key row) {
    this.type = type;
    this.row = row;
  }

  /**
   * Gives the key's name.
   *
   * @return the name, such as {@code keys/payments}
   */
  public String name() {
    return row.getName();
  }

  /**
   * Gives the key as a resource, on which a call checks the relation of its subject.
   *
   * @return the resource of the key's name
   */
  public Resource resource() {
    return type.resource(name());
  }

  /**
   * Gives the name of the version that encrypts and signs.
   *
   * @return the name, such as {@code keyVersions/payments-2}
   */
  public String primaryVersion() {
    return row.getPrimaryVersion();
  }

  /**
   * Gives the versions of the key.
   *
   * @return the versions, oldest first
   */
  public Future<List<KeyVersion>> versions() {
    return type.versions(name());
  }

  /**
   * Gives the primary version, after rotating the key when its rotation is due. The database decides when it is due,
   * and which of the calls that find it due rotates it; the time of this server only spares the others the asking.
   *
   * @return the primary version, which is the new one when the key rotated
   */
  public Future<KeyVersion> primary() {
    if (!row.hasNextRotationTime() || DatabaseTime.instant(row.getNextRotationTime()).isAfter(Instant.now())) {
      return type.version(primaryVersion());
    }
    return type.keys().claimRotation(row).compose(claimed -> claimed.isPresent() ? type.key(claimed.get()).rotate()
        : type.key(name())).compose(key -> type.version(key.primaryVersion()));
  }

  /**
   * Makes a new version and makes it the primary one. A key with a rotation period rotates next a period after this
   * rotation.
   *
   * @return the key, with its new primary version; when another change of the key came first, the key as that change
   * left it
   */
  public Future<Key> rotate() {
    return type.add(this).compose(this::point);
  }

  /**
   * Changes how often the key rotates on its own. The next rotation is a period after the change.
   *
   * @param period the period; zero for never
   * @param revision the revision that the change is made from
   * @return the changed key, or a failure with {@code INVALID_ARGUMENT} for a negative period, and with
   * {@code ABORTED} when the key changed since the revision
   */
  public Future<Key> update(Duration period, long revision) {
    if (period.isNegative()) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The rotation period is"
          + " negative."));
    }
    if (row.getRevision() != revision) {
      return Future.failedFuture(new PyriteException(GrpcStatus.ABORTED, "The key " + name() + " is at revision "
          + row.getRevision() + ", not " + revision + "."));
    }
    return save(row.toBuilder().setRotationPeriod(period.toSeconds()).clearNextRotationTime(), period);
  }

  /**
   * Gives the key as the service returns it.
   *
   * @return the key, with its next rotation time when it rotates on its own
   */
  public dev.horizon.pyrite.crypto.v1.Key message() {
    dev.horizon.pyrite.crypto.v1.Key.Builder message = dev.horizon.pyrite.crypto.v1.Key.newBuilder()
        .setName(name()).setParent(row.getParent())
        .setPurpose(dev.horizon.pyrite.crypto.v1.Key.Purpose.valueOf(row.getPurpose().name()))
        .setAlgorithm(dev.horizon.pyrite.crypto.v1.Algorithm.valueOf(row.getAlgorithm().name()))
        .setRotationPeriod(com.google.protobuf.Duration.newBuilder().setSeconds(row.getRotationPeriod()))
        .setPrimaryVersion(primaryVersion()).setRevision(row.getRevision())
        .setCreateTime(DatabaseTime.timestamp(row.getCreateTime())).setUpdateTime(DatabaseTime.timestamp(row.getUpdateTime()));
    if (row.hasNextRotationTime()) {
      message.setNextRotationTime(DatabaseTime.timestamp(row.getNextRotationTime()));
    }
    return message.build();
  }

  KeyType type() {
    return type;
  }

  /**
   * Gives the id of the key, which the names of its versions start with.
   *
   * @return the id, such as {@code payments}
   */
  String id() {
    return name().substring(name().indexOf('/') + 1);
  }

  Algorithm algorithm() {
    return Algorithm.valueOf(row.getAlgorithm().name());
  }

  /**
   * Makes a version the primary one. When another change came first, the key as it is now stands.
   */
  private Future<Key> point(KeyVersion version) {
    return save(row.toBuilder().setPrimaryVersion(version.name()).clearNextRotationTime(),
        Duration.ofSeconds(row.getRotationPeriod())).recover(
            failure -> failure instanceof PyriteException conflict
                && conflict.status() == GrpcStatus.ABORTED ? type.key(name()) : Future.failedFuture(failure));
  }

  /**
   * Writes a change of the key, with its next rotation a period from the change, on the clock of the database.
   *
   * @param period the rotation period; zero for none
   */
  private Future<Key> save(Tables.Key.Builder changed, Duration period) {
    Map<String, Expression> set = period.isPositive() ? Map.of("next_rotation_time", add(now(), period)) : Map.of();
    return type.keys().update(changed.build(), set, row.getUid(), row.getRevision(), name()).map(type::key);
  }
}
