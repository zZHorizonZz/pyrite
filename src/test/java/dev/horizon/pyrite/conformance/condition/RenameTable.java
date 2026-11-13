package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;

/**
 * Renames a table of the database, so that Pyrite cannot read it.
 */
public record RenameTable(String table, String to) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.pyrite().database.rename(table, to);
  }
}
