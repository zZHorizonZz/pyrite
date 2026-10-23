package dev.horizon.pyrite.impl.storage;

import static dev.horizon.basepoint.Filter.and;
import static dev.horizon.basepoint.Filter.eq;

import dev.horizon.cinder.Assignments;
import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;

public record FlowBackend(Database database) implements Backend<Tables.Flow, String> {

  @Override
  public Class<Tables.Flow> type() {
    return Tables.Flow.class;
  }

  public Future<Tables.Flow> save(Tables.Flow flow, long revision) {
    return update().where(and(eq("id", flow.getId()), eq("revision", revision))).set(flow, "*").returning()
        .map(updated -> updated.stream().findFirst().orElseThrow(() -> new PyriteException(GrpcStatus.ABORTED,
            "The flow flows/" + flow.getId() + " changed since revision " + revision + ".")));
  }

  public Future<Void> miss(Tables.Flow flow) {
    return update().where(and(eq("id", flow.getId()), eq("revision", flow.getRevision()), eq("answers",
        flow.getAnswers()))).set(Assignments.none().set("answers", flow.getAnswers() + 1)).returning()
        .compose(updated -> updated.isEmpty()
            ? Future.failedFuture(new PyriteException(GrpcStatus.ABORTED, "The flow flows/" + flow.getId()
                + " changed since revision " + flow.getRevision() + "."))
            : Future.succeededFuture());
  }
}
