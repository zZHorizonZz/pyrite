package dev.horizon.pyrite.conformance.handler;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AddRecorder;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectExchangeEnd;
import dev.horizon.pyrite.conformance.condition.ExpectSessionIssue;
import dev.horizon.pyrite.conformance.condition.Moment;
import dev.horizon.pyrite.conformance.condition.StartExchange;

@Summary("An interceptor of the end runs once, for the exchange and its account, and the declared steps run after it.")
final class ExchangeEndInterceptor extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new AddRecorder(Moment.END));
    call(new StartExchange("", "sign-in", EMAIL));
    callAndStopOnFailure(new AnswerPassword(PASSWORD), Requirement.HANDLER_EXCHANGE_END);
    callAndContinueOnFailure(new ExpectExchangeEnd("sign-in", "accounts/anne"), Requirement.HANDLER_EXCHANGE_END);
    callAndContinueOnFailure(new ExpectSessionIssue(), Requirement.HANDLER_EXCHANGE_END);
  }
}
