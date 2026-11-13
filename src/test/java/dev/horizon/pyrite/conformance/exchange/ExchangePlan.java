package dev.horizon.pyrite.conformance.exchange;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Plan;
import java.util.List;
import java.util.function.Supplier;

/**
 * The plan of exchanges: how they start, take answers, and end.
 */
public final class ExchangePlan implements Plan {

  @Override
  public String name() {
    return "exchanges";
  }

  @Override
  public List<Supplier<ConformanceModule>> modules() {
    return List.<Supplier<ConformanceModule>> of(DefinitionDeclaration::new, IdentifierKindMatch::new, AccountSubject::new,
        PhoneNumber::new, StaleRevision::new, AnswerRetry::new, AnswerOfOtherStage::new, UnreadableAnswer::new,
        DoneExchange::new,
        ExchangeExpiry::new, ExchangeName::new, FactorResult::new);
  }
}
