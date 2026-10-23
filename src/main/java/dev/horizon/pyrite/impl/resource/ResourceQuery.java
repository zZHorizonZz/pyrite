package dev.horizon.pyrite.impl.resource;

import com.google.protobuf.Struct;
import dev.horizon.aquery.aip158.Page;
import dev.horizon.aquery.aip158.PageRequest;
import dev.horizon.basepoint.Filter;
import dev.horizon.pyrite.impl.model.DeclaredType;
import dev.horizon.pyrite.impl.model.Leaf;
import dev.horizon.pyrite.impl.model.TypeSystem;
import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

record ResourceQuery<R extends Resource>(BaseResourceType<R> type, Subject subject, String permission,
    Struct context) {

  Future<Page<R>> page(PageRequest request, boolean showDeleted) {
    return anchors().compose(this::scope)
        .compose(scope -> scope.isEmpty() ? Future.succeededFuture(new Page<R>(List.of(), "", 0))
            : type.page(scope.get(), showDeleted, request).compose(this::filter));
  }

  private Future<Map<DeclaredType, Set<String>>> anchors() {
    Map<DeclaredType, Set<String>> anchors = new HashMap<>();
    Future<Void> listed = Future.succeededFuture();
    for (Leaf leaf : type.schema().types().leaves(type.declaration(), permission)) {
      String prefix = leaf.type().singular() + ":";
      listed = listed.compose(ignored -> type.schema().adapter().objects(leaf.type().singular(),
          leaf.relation(), subject.toString(), context).map(objects -> {
            Set<String> names = anchors.computeIfAbsent(leaf.type(), ignoredType -> new LinkedHashSet<>());
            objects.forEach(object -> names.add(leaf.type().resource(object.substring(prefix.length()))));
            return null;
          }));
    }
    return listed.map(anchors);
  }

  private Future<Optional<Filter>> scope(Map<DeclaredType, Set<String>> anchors) {
    TypeSystem types = type.schema().types();
    Set<DeclaredType> above = types.ancestors(type.declaration());
    Map<DeclaredType, Set<String>> known = new HashMap<>();
    above.forEach(ancestor -> known.put(ancestor, new LinkedHashSet<>(anchors.getOrDefault(ancestor, Set.of()))));
    return expand(above, known, 0).map(ignored -> {
      Set<String> names = new LinkedHashSet<>(anchors.getOrDefault(type.declaration(), Set.of()));
      Set<String> parents = new LinkedHashSet<>();
      types.parents(type.declaration()).forEach(parent -> parents.addAll(known.getOrDefault(parent, Set.of())));
      List<Filter> either = new ArrayList<>();
      if (!names.isEmpty()) {
        either.add(Filter.in(Resource.NAME, names));
      }
      if (!parents.isEmpty()) {
        either.add(Filter.in(Resource.PARENT, parents));
      }
      return either.isEmpty() ? Optional.empty() : Optional.of(Filter.or(either.toArray(Filter[]::new)));
    });
  }

  private Future<Void> expand(Set<DeclaredType> above, Map<DeclaredType, Set<String>> known, int depth) {
    if (depth >= type.schema().options().maxDepth()) {
      return Future.succeededFuture();
    }
    int before = known.values().stream().mapToInt(Set::size).sum();
    Future<Void> round = Future.succeededFuture();
    for (DeclaredType level : above) {
      round = round.compose(ignored -> {
        Set<String> parents = new LinkedHashSet<>();
        type.schema().types().parents(level).stream().filter(above::contains)
            .forEach(parent -> parents.addAll(known.get(parent)));
        return parents.isEmpty() ? Future.succeededFuture()
            : type.schema().all().stream().filter(candidate -> candidate.name().equals(level.name()))
                .findFirst().orElseThrow().beneath(parents).map(names -> known.get(level).addAll(names))
                .mapEmpty();
      });
    }
    return round.compose(ignored -> known.values().stream().mapToInt(Set::size).sum() == before
        ? Future.succeededFuture()
        : expand(above, known, depth + 1));
  }

  private Future<Page<R>> filter(Page<R> page) {
    List<Future<Boolean>> checks = page.items().stream()
        .map(resource -> resource.check(subject.toString(), permission, context)).toList();
    return Future.all(checks).map(done -> {
      List<R> allowed = new ArrayList<>();
      for (int index = 0; index < checks.size(); index++) {
        if (checks.get(index).result()) {
          allowed.add(page.items().get(index));
        }
      }
      return new Page<>(allowed, page.nextPageToken(), 0);
    });
  }
}
