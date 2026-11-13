package dev.horizon.pyrite.exchange;

import dev.horizon.pyrite.CodeContext;
import dev.horizon.pyrite.ExchangeContext;
import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The interceptors of the moments of exchanges: when one starts, when an answer meets a stage, when one ends, and when
 * one fails, and the interceptors that deliver one-time codes. Each moment runs its interceptors in the order they were
 * added. The interceptors of the end run before what the declaration of the exchange completes with, so that one of
 * them can stop the exchange before anything changes.
 *
 * <p>
 * A {@code remove} method removes the first interceptor that equals the one that it takes. Adding and removing is
 * safe while exchanges run; a run of a moment keeps the interceptors that it began with.
 */
public final class ExchangeInterceptor {

  private final List<Handler<ExchangeContext>> start = new CopyOnWriteArrayList<>();
  private final List<Handler<ExchangeContext>> stageEnd = new CopyOnWriteArrayList<>();
  private final List<Handler<ExchangeContext>> end = new CopyOnWriteArrayList<>();
  private final List<Handler<ExchangeContext>> failure = new CopyOnWriteArrayList<>();
  private final List<Handler<CodeContext>> code = new CopyOnWriteArrayList<>();

  public void addStart(Handler<ExchangeContext> interceptor) {
    start.add(Objects.requireNonNull(interceptor, "interceptor"));
  }

  public void removeStart(Handler<ExchangeContext> interceptor) {
    start.remove(interceptor);
  }

  public void addStageEnd(Handler<ExchangeContext> interceptor) {
    stageEnd.add(Objects.requireNonNull(interceptor, "interceptor"));
  }

  public void removeStageEnd(Handler<ExchangeContext> interceptor) {
    stageEnd.remove(interceptor);
  }

  public void addEnd(Handler<ExchangeContext> interceptor) {
    end.add(Objects.requireNonNull(interceptor, "interceptor"));
  }

  public void removeEnd(Handler<ExchangeContext> interceptor) {
    end.remove(interceptor);
  }

  public void addFailure(Handler<ExchangeContext> interceptor) {
    failure.add(Objects.requireNonNull(interceptor, "interceptor"));
  }

  public void removeFailure(Handler<ExchangeContext> interceptor) {
    failure.remove(interceptor);
  }

  public void addCode(Handler<CodeContext> interceptor) {
    code.add(Objects.requireNonNull(interceptor, "interceptor"));
  }

  public void removeCode(Handler<CodeContext> interceptor) {
    code.remove(interceptor);
  }

  Future<Void> start(ExchangeRecord exchange, Optional<Tables.Account> account) {
    return new ExchangeChain(start, exchange, account).run();
  }

  Future<Void> stageEnd(ExchangeRecord exchange, Optional<Tables.Account> account) {
    return new ExchangeChain(stageEnd, exchange, account).run();
  }

  Future<Void> end(ExchangeRecord exchange, Optional<Tables.Account> account) {
    return new ExchangeChain(end, exchange, account).run();
  }

  Future<Void> failure(ExchangeRecord exchange, Optional<Tables.Account> account) {
    return new ExchangeChain(failure, exchange, account).run();
  }

  /**
   * Gives a code to the interceptors that deliver codes.
   *
   * @return whether they took it; with none, the response of the exchange carries the code
   */
  Future<Boolean> deliver(Delivery delivery) {
    return code.isEmpty() ? Future.succeededFuture(false) : new CodeChain(code, delivery).run().map(true);
  }
}
