package dev.horizon.pyrite.impl.storage;

import dev.horizon.cinder.Attribute;
import dev.horizon.cinder.Field;
import dev.horizon.cinder.Model;
import dev.horizon.cinder.Relation;
import dev.horizon.pyrite.impl.model.DeclaredType;
import dev.horizon.pyrite.impl.resource.Resource;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class RowModel implements Model<Row> {

  private final String table;
  private final List<Field<Row, ?>> fields;

  RowModel(DeclaredType declared) {
    this.table = declared.table();
    this.fields = declared.fields().entrySet().stream()
        .<Field<Row, ?>> map(field -> new RowField(field.getKey(), type(field.getValue()))).toList();
  }

  @Override
  public Class<Row> type() {
    return Row.class;
  }

  @Override
  public String table() {
    return table;
  }

  @Override
  public List<Field<Row, ?>> fields() {
    return fields;
  }

  @Override
  public List<Field<Row, ?>> key() {
    return fields.stream().filter(Field::key).toList();
  }

  @Override
  public Optional<Field<Row, ?>> field(String name) {
    return fields.stream().filter(field -> field.name().equals(name)).findFirst();
  }

  @Override
  public List<Relation<?>> relations() {
    return List.of();
  }

  @Override
  public Optional<Relation<?>> relation(String name) {
    return Optional.empty();
  }

  @Override
  public Row assemble(Map<String, Object> values) {
    return new Row(values);
  }

  @Override
  public Row with(Row entity, String name, Object value) {
    return entity.with(name, value);
  }

  private static Class<?> type(dev.horizon.pyrite.schema.v1.Field.Kind kind) {
    return switch (kind) {
      case STRING -> String.class;
      case INTEGER -> Long.class;
      case DOUBLE -> Double.class;
      case BOOLEAN -> Boolean.class;
      case BYTES -> byte[].class;
      case TIMESTAMP -> Instant.class;
      case MAP -> JsonObject.class;
      case ARRAY -> JsonArray.class;
      case KIND_UNSPECIFIED, UNRECOGNIZED -> throw new IllegalStateException("A declared field has no kind");
    };
  }

  private record RowField(String name, Class<?> stored) implements Field<Row, Object> {

    @Override
    public String column() {
      return name;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<Object> type() {
      return (Class<Object>) stored;
    }

    @Override
    public boolean key() {
      return name.equals(Resource.UID);
    }

    @Override
    public boolean nullable() {
      return !key();
    }

    @Override
    public Object get(Row entity) {
      return entity.get(name);
    }

    @Override
    public <T> Optional<T> attribute(Attribute<T> attribute) {
      return Optional.empty();
    }
  }
}
