package dev.horizon.pyrite.conformance.list;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Plan;
import java.util.List;
import java.util.function.Supplier;

/**
 * The plan of lists: of grants, of subjects, and of changes.
 */
public final class ListPlan implements Plan {

  @Override
  public String name() {
    return "lists";
  }

  @Override
  public List<Supplier<ConformanceModule>> modules() {
    return List.<Supplier<ConformanceModule>> of(GrantFilter::new, GrantPaging::new, GrantListPermission::new,
        SubjectMembership::new, SubjectBlock::new, SubjectListRefusal::new, ChangeOrder::new, ChangePermission::new,
        ChangeTypeDeclaration::new);
  }
}
