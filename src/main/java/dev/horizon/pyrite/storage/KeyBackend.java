package dev.horizon.pyrite.storage;

import static dev.horizon.basepoint.expression.Expression.add;
import static dev.horizon.basepoint.expression.Expression.now;
import static dev.horizon.basepoint.filter.Filter.and;
import static dev.horizon.basepoint.filter.Filter.eq;
import static dev.horizon.basepoint.filter.Filter.lte;

import dev.horizon.cinder.Database;
import dev.horizon.pyrite.schema.keys.v1.Tables;
import io.vertx.core.Future;
import java.time.Duration;
import java.util.Optional;

/**
 * The rows of keys. The database's clock decides when a key rotates on its own.
 */
public record KeyBackend(Database database) implements ResourceBackend<Tables.Key> {

  @Override
  public Tables.Key prototype() {
    return Tables.Key.getDefaultInstance();
  }

  /**
   * Claims the rotation of a key whose time to rotate came: moves its next rotation to its period after the time of
   * the database. Of the calls that claim the same rotation, one gets the key.
   *
   * @param key the key, as it was read
   * @return the key with its next rotation moved on, or empty when its time had not come, or another call claimed it
   */
  public Future<Optional<Tables.Key>> claimRotation(Tables.Key key) {
    return returning(update().set("next_rotation_time", add(now(), Duration.ofSeconds(key.getRotationPeriod())))
        .where(and(eq("uid", key.getUid()), lte("next_rotation_time", now())))).map(
            rows -> rows.stream()
                .findFirst());
  }

  /**
   * Gives back the rotation of a key whose rotation failed after its claim: sets its next rotation time to what it was
   * before the claim, if the key is still as the claim left it, so that its next use rotates it again.
   *
   * @param claim the key as {@link #claimRotation(Tables.Key)} gave it
   * @param nextRotationTime the next rotation time before the claim
   * @return whether the key took the time back; not when another change came after the claim
   */
  public Future<Boolean> releaseRotation(Tables.Key claim, String nextRotationTime) {
    return execute(update().set("next_rotation_time", nextRotationTime).where(and(eq("uid", claim.getUid()),
        eq("revision", claim.getRevision())))).map(count -> count > 0);
  }
}
