package dev.horizon.pyrite.conformance.flow;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Plan;
import java.util.List;
import java.util.function.Supplier;

/**
 * The plan of flows: how they start, take answers, and end.
 */
public final class FlowPlan implements Plan {

  @Override
  public String name() {
    return "flows";
  }

  @Override
  public List<Supplier<ConformanceModule>> modules() {
    return List.<Supplier<ConformanceModule>> of(DefinitionDeclaration::new, IdentifierKindMatch::new, AccountSubject::new,
        PhoneNumber::new, StaleRevision::new, AnswerRetry::new, AnswerOfOtherStage::new, UnreadableAnswer::new, DoneFlow::new,
        FlowExpiry::new, FlowName::new, FactorResult::new);
  }
}
