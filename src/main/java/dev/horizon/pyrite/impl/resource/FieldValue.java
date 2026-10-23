package dev.horizon.pyrite.impl.resource;

import com.google.protobuf.ByteString;
import com.google.protobuf.NullValue;
import com.google.protobuf.Timestamp;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.data.v1.ArrayValue;
import dev.horizon.pyrite.data.v1.MapValue;
import dev.horizon.pyrite.data.v1.Value;
import dev.horizon.pyrite.schema.v1.Field;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

record FieldValue(Object value) {

  static FieldValue of(String field, Field.Kind kind, Value value) {
    if (value.hasNullValue()) {
      return new FieldValue(null);
    }
    Object stored = switch (kind) {
      case STRING -> value.hasStringValue() ? value.getStringValue() : null;
      case INTEGER -> value.hasIntegerValue() ? (Object) value.getIntegerValue() : null;
      case DOUBLE -> value.hasDoubleValue() ? (Object) value.getDoubleValue()
          : value.hasIntegerValue() ? (Object) (double) value.getIntegerValue() : null;
      case BOOLEAN -> value.hasBoolValue() ? (Object) value.getBoolValue() : null;
      case BYTES -> value.hasBytesValue() ? value.getBytesValue().toByteArray() : null;
      case TIMESTAMP -> value.hasTimestampValue() ? Instant.ofEpochSecond(value.getTimestampValue().getSeconds(),
          value.getTimestampValue().getNanos()) : null;
      case MAP -> value.hasMapValue() ? json(value) : null;
      case ARRAY -> value.hasArrayValue() ? json(value) : null;
      case KIND_UNSPECIFIED, UNRECOGNIZED -> throw new IllegalStateException("The field " + field + " has no kind.");
    };
    if (stored == null) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The field " + field + " holds "
          + kind.name().toLowerCase(Locale.ROOT) + " values, not " + value.getKindCase().name().toLowerCase(Locale.ROOT)
          + ".");
    }
    return new FieldValue(stored);
  }

  Value message() {
    return switch (value) {
      case null -> Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
      case Boolean value -> Value.newBuilder().setBoolValue(value).build();
      case Integer value -> Value.newBuilder().setIntegerValue(value).build();
      case Long value -> Value.newBuilder().setIntegerValue(value).build();
      case Number value -> Value.newBuilder().setDoubleValue(value.doubleValue()).build();
      case String value -> Value.newBuilder().setStringValue(value).build();
      case byte[] value -> Value.newBuilder().setBytesValue(ByteString.copyFrom(value)).build();
      case Instant value -> Value.newBuilder().setTimestampValue(Timestamp.newBuilder()
          .setSeconds(value.getEpochSecond()).setNanos(value.getNano())).build();
      case JsonArray value -> {
        ArrayValue.Builder array = ArrayValue.newBuilder();
        value.forEach(element -> array.addValues(new FieldValue(element).message()));
        yield Value.newBuilder().setArrayValue(array).build();
      }
      case JsonObject value -> {
        MapValue.Builder map = MapValue.newBuilder();
        value.forEach(entry -> map.putFields(entry.getKey(), new FieldValue(entry.getValue()).message()));
        yield Value.newBuilder().setMapValue(map).build();
      }
      default -> throw new IllegalStateException("A column holds a " + value.getClass().getName() + ", which no"
          + " field kind holds.");
    };
  }

  private static Object json(Value value) {
    return switch (value.getKindCase()) {
      case NULL_VALUE, KIND_NOT_SET -> null;
      case BOOL_VALUE -> value.getBoolValue();
      case INTEGER_VALUE -> value.getIntegerValue();
      case DOUBLE_VALUE -> value.getDoubleValue();
      case STRING_VALUE -> value.getStringValue();
      case BYTES_VALUE -> Base64.getEncoder().encodeToString(value.getBytesValue().toByteArray());
      case TIMESTAMP_VALUE -> Instant.ofEpochSecond(value.getTimestampValue().getSeconds(),
          value.getTimestampValue().getNanos()).toString();
      case ARRAY_VALUE -> {
        JsonArray array = new JsonArray();
        value.getArrayValue().getValuesList().forEach(element -> array.add(json(element)));
        yield array;
      }
      case MAP_VALUE -> {
        JsonObject map = new JsonObject();
        for (Map.Entry<String, Value> entry : value.getMapValue().getFieldsMap().entrySet()) {
          map.put(entry.getKey(), json(entry.getValue()));
        }
        yield map;
      }
    };
  }
}
