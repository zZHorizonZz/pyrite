package dev.horizon.pyrite.storage;

import static dev.horizon.basepoint.expression.Expression.now;
import static dev.horizon.basepoint.filter.Filter.and;
import static dev.horizon.basepoint.filter.Filter.eq;
import static dev.horizon.basepoint.filter.Filter.gt;

import com.google.protobuf.ByteString;
import dev.horizon.basepoint.UpdateQuery;
import dev.horizon.basepoint.expression.Expression;
import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Context;
import dev.horizon.cinder.Database;
import dev.horizon.cinder.Field;
import dev.horizon.cinder.Part;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The rows of flows. The database's clock decides when a flow and its code expire: the reads compare their expiries
 * with its own time.
 */
public record FlowBackend(Database database) implements Backend<Tables.Flow, String> {

  @Override
  public Tables.Flow prototype() {
    return Tables.Flow.getDefaultInstance();
  }

  /**
   * Inserts a flow.
   *
   * @param set the expressions of the fields that the database computes, by field, such as {@code expire_time}
   * @return the flow as the database holds it
   */
  public Future<Tables.Flow> start(Tables.Flow flow, Map<String, Expression> set) {
    return table().with(new Part() {
      @Override
      public Map<String, Object> write(Map<String, Object> fields, Context<?> context) {
        Map<String, Object> values = new LinkedHashMap<>(fields);
        values.putAll(set);
        return values;
      }
    }).insert(flow);
  }

  /**
   * Writes a flow over its row, if the row is still at a revision.
   *
   * @param revision the revision that the flow moved from
   * @param set the expressions of the fields that the database computes, by field, such as {@code code_expire_time}
   * @return the flow as the database holds it, or a failure with {@code ABORTED} when another answer came first
   */
  public Future<Tables.Flow> save(Tables.Flow flow, long revision, Map<String, Expression> set) {
    UpdateQuery update = update();
    for (Field<Tables.Flow, ?> field : model().fields()) {
      if (!field.key()) {
        update = update.set(field.name(), set.containsKey(field.name()) ? set.get(field.name()) : field.get(flow));
      }
    }
    return returning(update.where(and(eq("id", flow.getId()), eq("revision", revision)))).map(rows -> rows.stream()
        .findFirst().orElseThrow(() -> new PyriteException(GrpcStatus.ABORTED, "The flow flows/" + flow.getId()
            + " changed since revision " + revision + ".")));
  }

  /**
   * Keeps the response to the last answer of a flow, which a request with the same id gets again.
   */
  public Future<Void> remember(Tables.Flow flow, ByteString response) {
    return execute(update().set("response", response).where(and(eq("id", flow.getId()), eq("revision",
        flow.getRevision())))).mapEmpty();
  }

  /**
   * Reads a flow, if it has not expired.
   */
  public Future<Optional<Tables.Flow>> findLive(String id) {
    return first(query().where(and(eq("id", id), gt("expire_time", now()))));
  }

  /**
   * Tells whether the code of a flow has not expired.
   */
  public Future<Boolean> hasLiveCode(String id) {
    return exists(query().where(and(eq("id", id), gt("code_expire_time", now()))));
  }

  /**
   * Counts a wrong answer of a flow, if the flow is still at its revision and its count.
   *
   * @param flow the flow, as it was read
   * @return a future that completes once the count is one more, or a failure with {@code ABORTED} when another answer
   * came first
   */
  public Future<Void> miss(Tables.Flow flow) {
    return execute(update().set("answers", flow.getAnswers() + 1).where(and(eq("id", flow.getId()), eq("revision",
        flow.getRevision()), eq("answers", flow.getAnswers())))).compose(count -> count == 0
            ? Future.failedFuture(new PyriteException(GrpcStatus.ABORTED, "The flow flows/" + flow.getId()
                + " changed since revision " + flow.getRevision() + "."))
            : Future.succeededFuture());
  }
}
