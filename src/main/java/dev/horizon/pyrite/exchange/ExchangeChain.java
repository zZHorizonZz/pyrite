package dev.horizon.pyrite.exchange;

import dev.horizon.pyrite.ExchangeContext;
import dev.horizon.pyrite.identity.Account;
import io.vertx.core.Handler;
import java.util.List;
import java.util.Optional;

/**
 * A run of the interceptors of a moment of an exchange.
 */
final class ExchangeChain extends Chain<ExchangeContext> implements ExchangeContext {

  private final Exchange exchange;
  private final Optional<Account> account;

  ExchangeChain(List<Handler<ExchangeContext>> interceptors, Exchange exchange, Optional<Account> account) {
    super(interceptors);
    this.exchange = exchange;
    this.account = account;
  }

  @Override
  public String exchange() {
    return exchange.name();
  }

  @Override
  public String definition() {
    return exchange.definition().getName();
  }

  @Override
  public Optional<String> account() {
    return account.map(Account::name);
  }

  @Override
  ExchangeContext context() {
    return this;
  }

}
