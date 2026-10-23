package dev.horizon.pyrite.impl.storage;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class Row {

  private final Map<String, Object> values;

  public Row(Map<String, Object> values) {
    this.values = new HashMap<>(values);
  }

  public Object get(String field) {
    return values.get(field);
  }

  public String string(String field) {
    Object value = values.get(field);
    return value == null ? "" : (String) value;
  }

  public Map<String, Object> values() {
    return Collections.unmodifiableMap(values);
  }

  Row with(String field, Object value) {
    Map<String, Object> changed = new HashMap<>(values);
    changed.put(field, value);
    return new Row(changed);
  }
}
