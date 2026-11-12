package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;
import static dev.horizon.pyrite.PyriteFixture.CAROL;
import static dev.horizon.pyrite.PyriteFixture.DAVE;

import com.google.protobuf.Struct;
import dev.horizon.pyrite.Oracle;
import dev.horizon.pyrite.schema.v1.Declaration;
import dev.horizon.pyrite.schema.v1.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The resources and grants that Pyrite and OpenFGA both hold, for a check that the two agree: the oracle, which is
 * OpenFGA with the model of the declaration, the type of each resource, and the subject that administers it.
 */
public record AgreementWorld(Oracle oracle, Declaration declaration, Map<String, String> resources,
    Map<String, String> admins) {

  public static final List<String> PRINCIPALS = List.of(ANNE, BOB, CAROL, DAVE, "user:erin", "user:frank",
      "user:nobody");
  public static final List<Struct> TIMES = List.of(at("2026-05-01T00:00:00Z"), at("2026-07-01T00:00:00Z"));

  private static Struct at(String time) {
    return Struct.newBuilder().putFields("current_time", com.google.protobuf.Value.newBuilder().setStringValue(time)
        .build()).build();
  }

  /**
   * Gives the relations of a type, sorted.
   */
  public List<String> relations(String type) {
    Type declared = declaration.getTypesList().stream().filter(candidate -> candidate.getName().equals(type))
        .findFirst().orElseThrow();
    return new ArrayList<>(new TreeSet<>(declared.getDefinition().getRelationsMap().keySet()));
  }
}
