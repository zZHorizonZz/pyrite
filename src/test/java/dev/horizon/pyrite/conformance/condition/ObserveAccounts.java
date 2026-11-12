package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sets handlers of accounts that keep the names of the accounts created and deleted.
 */
public record ObserveAccounts() implements Condition {

  @Override
  public void evaluate(Environment environment) {
    List<String> creates = new CopyOnWriteArrayList<>();
    List<String> deletes = new CopyOnWriteArrayList<>();
    environment.put(Value.ACCOUNT_CREATES, creates);
    environment.put(Value.ACCOUNT_DELETES, deletes);
    environment.pyrite().service.accountCreateHandler(account -> creates.add(account.getName()))
        .accountDeleteHandler(account -> deletes.add(account.getName()));
  }
}
