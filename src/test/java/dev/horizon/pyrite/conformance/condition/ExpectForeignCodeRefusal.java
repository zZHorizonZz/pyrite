package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.authentication.v1.Flow;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import io.vertx.grpc.common.GrpcStatus;

/**
 * Answers the last flow with the code of a flow kept aside, and checks that it is refused. Both flows draw the same
 * code once in a million; then there is nothing to check.
 */
public record ExpectForeignCodeRefusal(Value<Flow> other) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    String foreign = environment.code(environment.get(other)).getCode();
    if (!foreign.equals(environment.code(environment.get(Value.FLOW)).getCode())) {
      new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerCodeText(foreign)).evaluate(environment);
    }
  }
}
