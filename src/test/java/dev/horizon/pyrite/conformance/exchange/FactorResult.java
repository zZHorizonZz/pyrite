package dev.horizon.pyrite.conformance.exchange;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.authentication.v1.Factor;
import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectFactorResult;
import dev.horizon.pyrite.conformance.condition.ExpectResultAccount;
import dev.horizon.pyrite.conformance.condition.StartExchange;

@Summary("An exchange that enrols a factor names it and its account in its result.")
final class FactorResult extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartExchange(ANNE, "enrol-password", ""));
    callAndStopOnFailure(new AnswerPassword("another correct horse battery"), Requirement.EXCHANGE_RESULT);
    callAndContinueOnFailure(new ExpectResultAccount("accounts/anne"), Requirement.EXCHANGE_RESULT);
    callAndContinueOnFailure(new ExpectFactorResult(Factor.Kind.PASSWORD, "accounts/anne"), Requirement.EXCHANGE_RESULT);
  }
}
