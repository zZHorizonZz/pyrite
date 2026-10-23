package dev.horizon.pyrite.impl.resource;

import com.google.protobuf.Struct;
import dev.horizon.spectrum.openfga.v1.Userset;
import io.vertx.core.Future;
import java.util.List;

record Check(String principal, Struct context) {

  Future<Boolean> on(Resource resource, String relation) {
    ResourceType<?> type = resource.type();
    if (!type.schema().types().readsFromParent(type.declaration(), relation)) {
      return type.schema().adapter().check(principal, relation, resource.object(), context);
    }
    return rewrite(resource, relation, type.schema().types().rewrite(type.declaration(), relation));
  }

  private Future<Boolean> rewrite(Resource resource, String relation, Userset userset) {
    return switch (userset.getUsersetCase()) {
      case THIS -> resource.type().schema().adapter().check(principal, relation, resource.object(), context);
      case COMPUTED_USERSET -> on(resource, userset.getComputedUserset().getRelation());
      case TUPLE_TO_USERSET -> resource.parent().compose(parent -> parent.isEmpty() ? Future.succeededFuture(false)
          : on(parent.get(), userset.getTupleToUserset().getComputedUserset().getRelation()));
      case UNION -> any(resource, relation, userset.getUnion().getChildList());
      case INTERSECTION -> all(resource, relation, userset.getIntersection().getChildList());
      case DIFFERENCE -> rewrite(resource, relation, userset.getDifference().getBase())
          .compose(base -> base ? rewrite(resource, relation, userset.getDifference().getSubtract())
              .map(subtract -> !subtract) : Future.succeededFuture(false));
      case USERSET_NOT_SET -> Future.succeededFuture(false);
    };
  }

  private Future<Boolean> any(Resource resource, String relation, List<Userset> children) {
    Future<Boolean> result = Future.succeededFuture(false);
    for (Userset child : children) {
      result = result.compose(found -> found ? Future.succeededFuture(true) : rewrite(resource, relation, child));
    }
    return result;
  }

  private Future<Boolean> all(Resource resource, String relation, List<Userset> children) {
    Future<Boolean> result = Future.succeededFuture(true);
    for (Userset child : children) {
      result = result.compose(found -> found ? rewrite(resource, relation, child) : Future.succeededFuture(false));
    }
    return result;
  }
}
