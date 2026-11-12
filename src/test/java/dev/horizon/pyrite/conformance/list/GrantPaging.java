package dev.horizon.pyrite.conformance.list;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;
import static dev.horizon.pyrite.PyriteFixture.CAROL;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectGrantPages;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import java.util.List;

@Summary("Grants are paged, each once.")
final class GrantPaging extends AccessModule {

  @Override
  protected void run() {
    prepareGroups();
    for (String user : List.of(BOB, CAROL, "user:dave", "user:erin")) {
      call(new GrantRelation(ANNE, "projects/ledger", "viewer", user));
    }
    callAndStopOnFailure(new ExpectGrantPages(ANNE, "projects/ledger", 2, List.of(ANNE, BOB, CAROL, "user:dave",
        "user:erin")), Requirement.GRANT_LIST);
  }
}
