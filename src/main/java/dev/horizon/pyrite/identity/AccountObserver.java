package dev.horizon.pyrite.identity;

import dev.horizon.pyrite.authentication.v1.Account;
import io.vertx.core.Handler;

/**
 * The handlers that observe accounts: one that Pyrite tells of each account that it creates, and one of each that it
 * deletes, through the authentication service or the access service. They hear of an account after the fact, so they
 * cannot stop it. A handler that throws changes nothing; the observer logs the throw.
 */
public final class AccountObserver {

  private static final System.Logger LOGGER = System.getLogger(AccountObserver.class.getName());

  private volatile Handler<Account> createHandler;
  private volatile Handler<Account> deleteHandler;

  /**
   * Sets the handler of created accounts, in place of the one before.
   *
   * @param handler the handler, or {@code null} for none
   */
  public void createHandler(Handler<Account> handler) {
    this.createHandler = handler;
  }

  /**
   * Sets the handler of deleted accounts, in place of the one before.
   *
   * @param handler the handler, or {@code null} for none
   */
  public void deleteHandler(Handler<Account> handler) {
    this.deleteHandler = handler;
  }

  void create(Account account) {
    tell(createHandler, account);
  }

  void delete(Account account) {
    tell(deleteHandler, account);
  }

  private static void tell(Handler<Account> handler, Account account) {
    if (handler == null) {
      return;
    }
    try {
      handler.handle(account);
    } catch (RuntimeException failure) {
      LOGGER.log(System.Logger.Level.ERROR, "A handler of accounts failed", failure);
    }
  }
}
