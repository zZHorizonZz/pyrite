package dev.horizon.pyrite.impl.storage;

import static dev.horizon.basepoint.Filter.and;
import static dev.horizon.basepoint.Filter.eq;
import static dev.horizon.basepoint.Filter.in;
import static dev.horizon.basepoint.Filter.isNotNull;
import static dev.horizon.basepoint.Filter.isNull;
import static dev.horizon.basepoint.Filter.lte;

import dev.horizon.aquery.aip158.Page;
import dev.horizon.aquery.aip158.PageRequest;
import dev.horizon.basepoint.Filter;
import dev.horizon.cinder.Assignments;
import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Database;
import dev.horizon.cinder.Model;
import dev.horizon.pyrite.impl.model.DeclaredType;
import dev.horizon.pyrite.impl.resource.Resource;
import io.vertx.core.Future;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record ResourceBackend(Database database, RowModel rows) implements Backend<Row, String> {

  public ResourceBackend(Database database, DeclaredType declared) {
    this(database, new RowModel(declared));
  }

  @Override
  public Class<Row> type() {
    return Row.class;
  }

  @Override
  public Model<Row> model() {
    return rows;
  }

  public Future<Optional<Row>> first(Filter where) {
    return find(where).first();
  }

  public Future<Boolean> taken(String name) {
    return find(eq(Resource.NAME, name)).exists();
  }

  public Future<Optional<Row>> change(String uid, long revision, Map<String, Object> changes, Instant now) {
    Assignments set = Assignments.none();
    for (Map.Entry<String, Object> change : changes.entrySet()) {
      set = change.getValue() == null ? set.unset(change.getKey()) : set.set(change.getKey(), change.getValue());
    }
    set = set.set(Resource.REVISION, revision + 1).set(Resource.UPDATE_TIME, now);
    return update().where(and(eq(Resource.UID, uid), eq(Resource.REVISION, revision))).set(set).returning()
        .map(updated -> updated.stream().findFirst());
  }

  public Future<Boolean> occupies(String parent) {
    return find(and(eq(Resource.PARENT, parent), isNull(Resource.DELETE_TIME))).exists();
  }

  public Future<List<Row>> under(String parent) {
    return find(eq(Resource.PARENT, parent)).list();
  }

  public Future<Set<String>> beneath(Collection<String> parents) {
    return find(in(Resource.PARENT, parents)).list()
        .map(found -> found.stream().map(row -> row.string(Resource.NAME)).collect(Collectors
            .toCollection(LinkedHashSet::new)));
  }

  public Future<Page<Row>> page(Filter scope, boolean showDeleted, PageRequest request) {
    return find(showDeleted ? scope : and(scope, isNull(Resource.DELETE_TIME))).page(request);
  }

  public Future<List<Row>> due(Instant now) {
    return find(and(isNotNull(Resource.PURGE_TIME), lte(Resource.PURGE_TIME, now))).list();
  }

  public Future<Void> remove(String uid) {
    return delete().where(eq(Resource.UID, uid)).execute().mapEmpty();
  }

  public Future<Void> removeBeneath(String parent) {
    return delete().where(eq(Resource.PARENT, parent)).execute().mapEmpty();
  }
}
