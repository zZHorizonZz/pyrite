package dev.horizon.pyrite.impl.resource;

import com.google.protobuf.Struct;
import dev.horizon.pyrite.impl.model.Leaf;
import dev.horizon.spectrum.openfga.v1.User;
import dev.horizon.spectrum.openfga.v1.UserTypeFilter;
import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

record SubjectQuery(Resource resource, String permission, UserTypeFilter filter, Struct context) {

  Future<List<String>> subjects() {
    return resource.ancestors().compose(ancestors -> {
      List<Resource> chain = new ArrayList<>(List.of(resource));
      chain.addAll(ancestors);
      Set<String> candidates = new TreeSet<>();
      Future<Void> listed = Future.succeededFuture();
      for (Leaf leaf : resource.type().schema().types().leaves(resource.type().declaration(), permission)) {
        for (Resource holder : chain) {
          if (holder.type().name().equals(leaf.type().name())) {
            listed = listed.compose(ignored -> resource.type().schema().adapter()
                .users(leaf.type().singular(), holder.id(), leaf.relation(), filter, context)
                .map(users -> {
                  users.forEach(user -> candidates.add(text(user)));
                  return null;
                }));
          }
        }
      }
      return listed.compose(ignored -> verify(new ArrayList<>(candidates)));
    });
  }

  private Future<List<String>> verify(List<String> candidates) {
    List<Future<Boolean>> checks = candidates.stream().map(candidate -> candidate.endsWith(":*")
        ? Future.succeededFuture(true)
        : resource.check(candidate, permission, context)).toList();
    return Future.all(checks).map(done -> {
      List<String> subjects = new ArrayList<>();
      for (int index = 0; index < candidates.size(); index++) {
        if (checks.get(index).result()) {
          subjects.add(candidates.get(index));
        }
      }
      return subjects;
    });
  }

  private static String text(User user) {
    return switch (user.getUserCase()) {
      case OBJECT -> user.getObject().getType() + ":" + user.getObject().getId();
      case USERSET -> user.getUserset().getType() + ":" + user.getUserset().getId() + "#"
          + user.getUserset().getRelation();
      case WILDCARD -> user.getWildcard().getType() + ":*";
      case USER_NOT_SET -> "";
    };
  }
}
