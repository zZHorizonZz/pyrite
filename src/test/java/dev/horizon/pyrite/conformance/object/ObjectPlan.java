package dev.horizon.pyrite.conformance.object;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Plan;
import java.util.List;
import java.util.function.Supplier;

/**
 * The plan of objects: how resources are made known, forgotten, and listed.
 */
public final class ObjectPlan implements Plan {

  @Override
  public String name() {
    return "objects";
  }

  @Override
  public List<Supplier<ConformanceModule>> modules() {
    return List.<Supplier<ConformanceModule>> of(ParentInheritance::new, ObjectOnce::new, CreatePermission::new,
        ObjectParent::new, ObjectName::new, ObjectForgetting::new, ObjectWithChildren::new, UnknownObject::new,
        DeletePermission::new, ObjectList::new, ObjectListPermission::new);
  }
}
