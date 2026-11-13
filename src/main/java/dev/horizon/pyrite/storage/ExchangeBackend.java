package dev.horizon.pyrite.storage;

import static dev.horizon.basepoint.expression.Expression.now;
import static dev.horizon.basepoint.filter.Filter.and;
import static dev.horizon.basepoint.filter.Filter.eq;
import static dev.horizon.basepoint.filter.Filter.gt;
import static dev.horizon.basepoint.filter.Filter.lt;
import static dev.horizon.basepoint.filter.Filter.lte;

import com.google.protobuf.ByteString;
import dev.horizon.basepoint.UpdateQuery;
import dev.horizon.basepoint.expression.Expression;
import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Database;
import dev.horizon.cinder.Field;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Map;
import java.util.Optional;

/**
 * The rows of exchanges. The database's clock decides when an exchange and the nonce of its stage expire: the reads
 * compare their expiries with its own time.
 */
public record ExchangeBackend(Database database) implements Backend<Tables.Exchange, String> {

  @Override
  public Tables.Exchange prototype() {
    return Tables.Exchange.getDefaultInstance();
  }

  /**
   * Inserts an exchange.
   *
   * @param set the expressions of the fields that the database computes, by field, such as {@code expire_time}
   * @return the exchange as the database holds it
   */
  public Future<Tables.Exchange> start(Tables.Exchange exchange, Map<String, Expression> set) {
    return table().with(new ExpressionPart(set)).insert(exchange);
  }

  /**
   * Writes an exchange over its row, if the row is still at a revision.
   *
   * @param revision the revision that the exchange moved from
   * @param set the expressions of the fields that the database computes, by field, such as {@code nonce_expire_time}
   * @return the exchange as the database holds it, or a failure with {@code ABORTED} when another answer came first
   */
  public Future<Tables.Exchange> save(Tables.Exchange exchange, long revision, Map<String, Expression> set) {
    UpdateQuery update = update();
    for (Field<Tables.Exchange, ?> field : model().fields()) {
      if (!field.key()) {
        update = update.set(field.name(), set.containsKey(field.name()) ? set.get(field.name()) : field.get(exchange));
      }
    }
    return returning(update.where(and(eq("id", exchange.getId()), eq("revision", revision)))).map(rows -> rows.stream()
        .findFirst().orElseThrow(() -> new PyriteException(GrpcStatus.ABORTED, "The exchange exchanges/" + exchange.getId()
            + " changed since revision " + revision + ".")));
  }

  /**
   * Keeps the response to the last answer of an exchange, which a request with the same id gets again.
   *
   * @param response the response, encrypted
   * @return a future that completes once the response is kept, or a failure with {@code ABORTED} when another answer
   * moved the exchange on
   */
  public Future<Void> remember(Tables.Exchange exchange, ByteString response) {
    return execute(update().set("response", response).where(and(eq("id", exchange.getId()), eq("revision",
        exchange.getRevision())))).compose(count -> count == 0 ? aborted(exchange.getId(), exchange.getRevision())
            : Future.succeededFuture());
  }

  /**
   * Reads an exchange, if it has not expired.
   */
  public Future<Optional<Tables.Exchange>> findLive(String id) {
    return first(query().where(and(eq("id", id), gt("expire_time", now()))));
  }

  /**
   * Tells whether the nonce of the stage of an exchange has not expired.
   */
  public Future<Boolean> hasLiveNonce(String id) {
    return exists(query().where(and(eq("id", id), gt("nonce_expire_time", now()))));
  }

  /**
   * Counts an answer of an exchange before it is verified, in one statement that also checks the count, so that answers
   * at once cannot pass the most wrong answers. A right answer gives its count back when it saves the exchange.
   *
   * @param exchange the exchange, as it was read
   * @param maxAnswers the most wrong answers that an exchange takes
   * @return whether the answer was counted; false when the exchange moved to another revision, or took the most
   * answers
   */
  public Future<Boolean> attempt(Tables.Exchange exchange, int maxAnswers) {
    return execute(update().increment("answers", 1).where(and(eq("id", exchange.getId()), eq("revision",
        exchange.getRevision()), lt("answers", maxAnswers)))).map(count -> count > 0);
  }

  /**
   * Deletes the rows of exchanges that expired. An expired exchange takes no answers, so its row, and the response that
   * it keeps, serves nothing.
   *
   * @return the number of rows
   */
  public Future<Long> purge() {
    return execute(delete().where(lte("expire_time", now())));
  }

  private static <T> Future<T> aborted(String id, long revision) {
    return Future.failedFuture(new PyriteException(GrpcStatus.ABORTED, "The exchange exchanges/" + id
        + " changed since revision " + revision + "."));
  }
}
