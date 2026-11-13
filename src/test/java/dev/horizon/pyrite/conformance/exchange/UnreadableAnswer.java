package dev.horizon.pyrite.conformance.exchange;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerAny;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An answer that does not parse as its message is refused.")
final class UnreadableAnswer extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartExchange("", "sign-in", EMAIL));
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new AnswerAny("a PasswordAnswer of broken"
        + " bytes",
        Any.newBuilder().setTypeUrl("type.googleapis.com/pyrite.authentication.v1.PasswordAnswer")
            .setValue(ByteString.copyFrom(new byte[] { (byte) 0xff, (byte) 0xff, 0x01 })).build())),
        Requirement.EXCHANGE_ANSWER);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new AnswerAny("an empty Any",
        Any.getDefaultInstance())), Requirement.EXCHANGE_ANSWER);
  }
}
