package dev.horizon.pyrite.conformance;

import dev.horizon.pyrite.database.TestDatabase;
import java.util.Arrays;
import java.util.List;

/**
 * One combination of the ways that a module can run: the database under Pyrite, how codes reach the application, and
 * how the application reaches Pyrite. Each module of a plan runs once in every variant, so a behavior holds whichever
 * way Pyrite is deployed.
 */
public record Variant(TestDatabase database, CodeDelivery delivery, Transport transport) {

  /**
   * Gives every variant on a database.
   */
  public static List<Variant> on(TestDatabase database) {
    return Arrays.stream(CodeDelivery.values()).flatMap(delivery -> Arrays.stream(Transport.values())
        .map(transport -> new Variant(database, delivery, transport))).toList();
  }

  @Override
  public String toString() {
    return "database=" + database.name() + ", delivery=" + delivery.name().toLowerCase() + ", transport="
        + transport.name().toLowerCase();
  }
}
