package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Struct;
import dev.horizon.pyrite.access.v1.CheckRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Checks every relation of every resource of the world, for every principal, at every time, in Pyrite and in OpenFGA,
 * and checks that the two agree.
 */
public record ExpectCheckAgreement() implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    AgreementWorld world = environment.get(Value.WORLD);
    List<String> disagreements = new ArrayList<>();
    for (Struct time : AgreementWorld.TIMES) {
      for (Map.Entry<String, String> resource : world.resources().entrySet()) {
        for (String relation : world.relations(resource.getValue())) {
          for (String principal : AgreementWorld.PRINCIPALS) {
            boolean expected = world.oracle().check(principal, resource.getKey(), relation, time);
            boolean actual = environment.pyrite().access.check(CheckRequest.newBuilder().setSubject(principal)
                .setObject(resource.getKey()).setPermission(relation).setContext(time).build()).await(TIMEOUT)
                .getAllowed();
            if (actual != expected) {
              disagreements.add(principal + " " + relation + " " + resource.getKey() + " at "
                  + time.getFieldsOrThrow("current_time").getStringValue() + ": Pyrite " + actual + ", OpenFGA "
                  + expected);
            }
          }
        }
      }
    }
    assertThat(disagreements).isEmpty();
  }
}
