package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.ContinueFlowRequest;
import dev.horizon.pyrite.authentication.v1.Flow;
import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.UUID;

/**
 * Answers the last flow with a password twice, with the same request id, and checks that the second gets the
 * response of the first.
 */
public record ExpectRetryReplay(String password) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    Flow flow = environment.get(Value.FLOW);
    ContinueFlowRequest request = ContinueFlowRequest.newBuilder().setFlow(flow.getName())
        .setRevision(flow.getRevision()).setRequestId(UUID.randomUUID().toString()).setAnswer(Any.pack(
            PasswordAnswer.newBuilder().setPassword(password).build()))
        .build();
    Flow first = environment.continueFlow(request);
    assertThat(environment.continueFlow(request)).isEqualTo(first);
  }
}
