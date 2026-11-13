package dev.horizon.pyrite.conformance.handler;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AddRefusal;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectNoSessionIssue;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.Moment;
import dev.horizon.pyrite.conformance.condition.ObserveSessions;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An interceptor of the end can stop the exchange with its own status, before the exchange issues a session.")
final class ExchangeEndRefusal extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new ObserveSessions());
    call(new AddRefusal(Moment.END, GrpcStatus.FAILED_PRECONDITION));
    call(new StartExchange("", "sign-in", EMAIL));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.FAILED_PRECONDITION, new AnswerPassword(PASSWORD)),
        Requirement.HANDLER_EXCHANGE_END);
    callAndContinueOnFailure(new ExpectNoSessionIssue(), Requirement.HANDLER_EXCHANGE_END);
  }
}
