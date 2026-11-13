package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Struct;
import dev.horizon.pyrite.access.v1.ListSubjectsRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Lists the users of every relation of every resource of the world, and checks that each list is exactly OpenFGA's.
 */
public record ExpectSubjectAgreement() implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    AgreementWorld world = environment.get(Value.WORLD);
    Struct time = AgreementWorld.TIMES.getFirst();
    List<String> disagreements = new ArrayList<>();
    for (Map.Entry<String, String> resource : world.resources().entrySet()) {
      for (String relation : world.relations(resource.getValue())) {
        Set<String> expected = world.oracle().users(resource.getKey(), relation, time);
        Set<String> actual = new TreeSet<>(environment.pyrite().access.listSubjects(ListSubjectsRequest.newBuilder()
            .setSubject(world.admins().get(resource.getKey())).setObject(resource.getKey()).setPermission(relation)
            .setSubjectType("user").setContext(time).build()).await(TIMEOUT).getSubjectsList());
        if (!actual.equals(expected)) {
          disagreements.add(relation + " " + resource.getKey() + ": Pyrite " + actual + ", OpenFGA " + expected);
        }
      }
    }
    assertThat(disagreements).isEmpty();
  }
}
