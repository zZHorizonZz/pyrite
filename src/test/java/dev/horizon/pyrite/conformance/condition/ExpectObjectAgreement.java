package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Struct;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import io.vertx.grpc.client.InvalidStatusException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Lists the objects of every type, relation and principal of the world, and checks that each list is exactly what
 * OpenFGA's check allows.
 */
public record ExpectObjectAgreement() implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    AgreementWorld world = environment.get(Value.WORLD);
    Struct time = AgreementWorld.TIMES.getFirst();
    List<String> disagreements = new ArrayList<>();
    for (String type : new TreeSet<>(world.resources().values())) {
      for (String relation : world.relations(type)) {
        for (String principal : AgreementWorld.PRINCIPALS) {
          Set<String> expected = new TreeSet<>();
          for (Map.Entry<String, String> resource : world.resources().entrySet()) {
            if (resource.getValue().equals(type) && world.oracle().check(principal, resource.getKey(), relation,
                time)) {
              expected.add(resource.getKey());
            }
          }
          try {
            Set<String> actual = new TreeSet<>(ListObjects.names(environment, principal, type, relation, time));
            if (!actual.equals(expected)) {
              disagreements.add(principal + " " + relation + " " + type + ": Pyrite " + actual + ", OpenFGA "
                  + expected);
            }
          } catch (InvalidStatusException failed) {
            disagreements.add(principal + " " + relation + " " + type + ": Pyrite fails with "
                + failed.actualStatus() + ", OpenFGA " + expected);
          }
        }
      }
    }
    assertThat(disagreements).isEmpty();
  }
}
