package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.authentication.v1.Flow;
import dev.horizon.pyrite.authentication.v1.RecoveryCodes;
import dev.horizon.pyrite.authentication.v1.StartFlowRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.List;

/**
 * Enrols recovery codes for a subject, and keeps them.
 */
public record EnrolRecoveryCodes(String subject, Value<List<String>> into) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    Flow done = environment.start(StartFlowRequest.newBuilder().setSubject(subject)
        .setDefinition("enrol-recovery-codes").build());
    environment.put(into, List.copyOf(done.getResult().getIssued(0).unpack(RecoveryCodes.class).getCodesList()));
  }
}
