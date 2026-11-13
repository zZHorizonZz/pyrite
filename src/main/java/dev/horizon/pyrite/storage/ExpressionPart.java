package dev.horizon.pyrite.storage;

import dev.horizon.basepoint.expression.Expression;
import dev.horizon.cinder.Context;
import dev.horizon.cinder.Part;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The part of an insert that writes some fields as expressions that the database computes, such as
 * {@code expire_time} as {@code add(now(), lifetime)}, in place of the values of the row.
 *
 * @param expressions the expressions, by field
 */
record ExpressionPart(Map<String, Expression> expressions) implements Part {

  @Override
  public Map<String, Object> write(Map<String, Object> fields, Context<?> context) {
    Map<String, Object> values = new LinkedHashMap<>(fields);
    values.putAll(expressions);
    return values;
  }
}
