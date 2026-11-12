package dev.horizon.pyrite.conformance.handler;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.CreateAccount;
import dev.horizon.pyrite.conformance.condition.DeleteAccount;
import dev.horizon.pyrite.conformance.condition.ExpectNames;
import dev.horizon.pyrite.conformance.condition.ObserveAccounts;
import java.util.List;

@Summary("The handlers of accounts hear of each account that is created and deleted.")
final class AccountObservation extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new ObserveAccounts());
    call(new CreateAccount("bob", "bob@acme.dev", "another long passphrase"));
    call(new DeleteAccount("user:bob", "accounts/bob"));
    callAndContinueOnFailure(new ExpectNames(Value.ACCOUNT_CREATES, List.of("accounts/bob")), Requirement.HANDLER_ACCOUNT);
    callAndContinueOnFailure(new ExpectNames(Value.ACCOUNT_DELETES, List.of("accounts/bob")), Requirement.HANDLER_ACCOUNT);
  }
}
