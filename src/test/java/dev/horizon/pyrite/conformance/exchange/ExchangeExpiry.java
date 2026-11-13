package dev.horizon.pyrite.conformance.exchange;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.MakeExchangeOlder;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;

@Summary("An expired exchange takes no answer, not even the right one.")
final class ExchangeExpiry extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartExchange("", "sign-in", EMAIL));
    call(new MakeExchangeOlder(Duration.ofMinutes(16)));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.FAILED_PRECONDITION, new AnswerPassword(PASSWORD)),
        Requirement.EXCHANGE_EXPIRY);
  }
}
