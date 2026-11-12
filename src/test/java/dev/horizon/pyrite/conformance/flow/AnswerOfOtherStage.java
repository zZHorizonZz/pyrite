package dev.horizon.pyrite.conformance.flow;

import com.google.protobuf.Any;
import com.google.protobuf.StringValue;
import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerAny;
import dev.horizon.pyrite.conformance.condition.AnswerCodeText;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.StartFlow;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An answer that the stage does not take is refused.")
final class AnswerOfOtherStage extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartFlow("", "sign-in", EMAIL));
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new AnswerCodeText("123456")),
        Requirement.FLOW_ANSWER);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new AnswerAny("a StringValue",
        Any.pack(StringValue.of(PASSWORD)))), Requirement.FLOW_ANSWER);
  }
}
