package dev.horizon.pyrite.conformance.condition;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.ContinueFlowRequest;
import dev.horizon.pyrite.authentication.v1.Flow;
import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.UUID;

/**
 * Answers the last flow as it started with a password, at its revision then, later by a number of revisions.
 */
public record AnswerAtRevision(long later, String password) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    Flow start = environment.get(Value.FLOW_START);
    environment.continueFlow(ContinueFlowRequest.newBuilder().setFlow(start.getName())
        .setRevision(start.getRevision() + later).setRequestId(UUID.randomUUID().toString())
        .setAnswer(Any.pack(PasswordAnswer.newBuilder().setPassword(password).build())).build());
  }
}
