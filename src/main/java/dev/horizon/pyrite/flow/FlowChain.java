package dev.horizon.pyrite.flow;

import dev.horizon.pyrite.FlowContext;
import dev.horizon.pyrite.authentication.v1.Result;
import dev.horizon.pyrite.identity.Account;
import io.vertx.core.Handler;
import java.util.List;
import java.util.Optional;

/**
 * A run of the interceptors of a moment of a flow.
 */
final class FlowChain extends Chain<FlowContext> implements FlowContext {

  private final Flow flow;
  private final Optional<Account> account;
  private final Result.Builder result;

  FlowChain(List<Handler<FlowContext>> interceptors, Flow flow, Optional<Account> account, Result.Builder result) {
    super(interceptors);
    this.flow = flow;
    this.account = account;
    this.result = result;
  }

  @Override
  public String flow() {
    return flow.name();
  }

  @Override
  public String definition() {
    return flow.definition();
  }

  @Override
  public Optional<String> account() {
    return account.map(Account::name);
  }

  @Override
  public Result.Builder result() {
    return result;
  }

  @Override
  FlowContext context() {
    return this;
  }

  Flow flowInstance() {
    return flow;
  }

  Optional<Account> accountInstance() {
    return account;
  }
}
