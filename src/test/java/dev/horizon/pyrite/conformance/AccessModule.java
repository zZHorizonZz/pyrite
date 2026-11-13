package dev.horizon.pyrite.conformance;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.condition.CreateObject;

/**
 * A scenario of access, which starts with anne's organization acme and its project ledger.
 */
public abstract class AccessModule extends ConformanceModule {

  /**
   * Makes acme and ledger known, as anne.
   */
  protected final void prepareLedger() {
    call(new CreateObject(ANNE, "organizations/acme", ""));
    call(new CreateObject(ANNE, "projects/ledger", "organizations/acme"));
  }

  /**
   * Makes acme, ledger and the groups finance and audit known, as anne.
   */
  protected final void prepareGroups() {
    prepareLedger();
    call(new CreateObject(ANNE, "groups/finance", "organizations/acme"));
    call(new CreateObject(ANNE, "groups/audit", "organizations/acme"));
  }
}
