package dev.horizon.pyrite.exchange;

import dev.horizon.pyrite.ExchangeContext;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Handler;
import java.util.List;
import java.util.Optional;

/**
 * A run of the interceptors of a moment of an exchange.
 */
final class ExchangeChain extends Chain<ExchangeContext> {

  private final ExchangeRecord exchange;
  private final Optional<Tables.Account> account;

  ExchangeChain(List<Handler<ExchangeContext>> interceptors, ExchangeRecord exchange,
      Optional<Tables.Account> account) {
    super(interceptors);
    this.exchange = exchange;
    this.account = account;
  }

  @Override
  ExchangeContext context(Turn turn) {
    return new ExchangeContext() {

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
        return account.map(Tables.Account::getName);
      }

      @Override
      public void next() {
        turn.next();
      }

      @Override
      public void fail(Throwable cause) {
        turn.fail(cause);
      }
    };
  }
}
