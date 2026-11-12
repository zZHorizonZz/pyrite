package dev.horizon.pyrite.key;

import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * The claims of a JWT, as the JSON of the token and as the {@code Struct} of the service. A {@code Struct} holds every
 * number as a double; a whole number below 2<sup>53</sup> becomes a long in the JSON, so that {@code exp} reads
 * {@code 1767225600}, not {@code 1.7672256E9}.
 *
 * @param json the claims
 */
record ClaimSet(JsonObject json) {

  private static final double LARGEST_EXACT_INTEGER = 9.007199254740992E15;

  public static ClaimSet of(Struct struct) {
    JsonObject json = new JsonObject();
    struct.getFieldsMap().forEach((name, value) -> json.put(name, plain(value)));
    return new ClaimSet(json);
  }

  public Struct struct() {
    return struct(json);
  }

  private static Struct struct(JsonObject json) {
    Struct.Builder struct = Struct.newBuilder();
    json.forEach(entry -> struct.putFields(entry.getKey(), value(entry.getValue())));
    return struct.build();
  }

  private static Value value(Object plain) {
    Value.Builder value = Value.newBuilder();
    switch (plain) {
      case null -> value.setNullValue(NullValue.NULL_VALUE);
      case Boolean flag -> value.setBoolValue(flag);
      case Number number -> value.setNumberValue(number.doubleValue());
      case JsonArray array -> {
        ListValue.Builder list = ListValue.newBuilder();
        array.forEach(element -> list.addValues(value(element)));
        value.setListValue(list);
      }
      case JsonObject object -> value.setStructValue(struct(object));
      default -> value.setStringValue(plain.toString());
    }
    return value.build();
  }

  private static Object plain(Value value) {
    return switch (value.getKindCase()) {
      case NULL_VALUE, KIND_NOT_SET -> null;
      case BOOL_VALUE -> value.getBoolValue();
      case NUMBER_VALUE -> {
        double number = value.getNumberValue();
        yield number == Math.rint(number) && Math.abs(number) < LARGEST_EXACT_INTEGER ? (Object) (long) number
            : (Object) number;
      }
      case STRING_VALUE -> value.getStringValue();
      case LIST_VALUE -> {
        JsonArray array = new JsonArray();
        value.getListValue().getValuesList().forEach(element -> array.add(plain(element)));
        yield array;
      }
      case STRUCT_VALUE -> of(value.getStructValue()).json();
    };
  }
}
