package dev.horizon.pyrite.flow;

import dev.horizon.pyrite.CodeContext;
import dev.horizon.pyrite.CompletionHandler;
import dev.horizon.pyrite.FlowContext;
import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.authentication.v1.Result;
import dev.horizon.pyrite.identity.Account;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The interceptors of the moments of flows: when one starts, when an answer meets a stage, when one ends, and when one
 * fails, and the interceptors that deliver one-time codes. Each moment runs its interceptors in the order they were
 * added; the end of a flow starts with a {@link CompletionHandler}.
 *
 * <p>
 * A {@code remove} method removes the first interceptor that equals the one that it takes. Every
 * {@code CompletionHandler} equals every other, so {@code removeEnd(CompletionHandler.create())} removes the one of
 * the end. Adding and removing is safe while flows run; a run of a moment keeps the interceptors that it began with.
 */
public final class FlowInterceptor {

  private final List<Handler<FlowContext>> start = new CopyOnWriteArrayList<>();
  private final List<Handler<FlowContext>> stageEnd = new CopyOnWriteArrayList<>();
  private final List<Handler<FlowContext>> end = new CopyOnWriteArrayList<>(List.of(CompletionHandler.create()));
  private final List<Handler<FlowContext>> failure = new CopyOnWriteArrayList<>();
  private final List<Handler<CodeContext>> code = new CopyOnWriteArrayList<>();

  public void addStart(Handler<FlowContext> interceptor) {
    start.add(interceptor);
  }

  public void removeStart(Handler<FlowContext> interceptor) {
    start.remove(interceptor);
  }

  public void addStageEnd(Handler<FlowContext> interceptor) {
    stageEnd.add(interceptor);
  }

  public void removeStageEnd(Handler<FlowContext> interceptor) {
    stageEnd.remove(interceptor);
  }

  public void addEnd(Handler<FlowContext> interceptor) {
    end.add(interceptor);
  }

  public void removeEnd(Handler<FlowContext> interceptor) {
    end.remove(interceptor);
  }

  public void addFailure(Handler<FlowContext> interceptor) {
    failure.add(interceptor);
  }

  public void removeFailure(Handler<FlowContext> interceptor) {
    failure.remove(interceptor);
  }

  public void addCode(Handler<CodeContext> interceptor) {
    code.add(interceptor);
  }

  public void removeCode(Handler<CodeContext> interceptor) {
    code.remove(interceptor);
  }

  Future<Void> start(Flow flow, Optional<Account> account) {
    return new FlowChain(start, flow, account, Result.newBuilder()).run();
  }

  Future<Void> stageEnd(Flow flow, Optional<Account> account) {
    return new FlowChain(stageEnd, flow, account, Result.newBuilder()).run();
  }

  Future<Void> end(Flow flow, Optional<Account> account, Result.Builder result) {
    return new FlowChain(end, flow, account, result).run();
  }

  Future<Void> failure(Flow flow, Optional<Account> account) {
    return new FlowChain(failure, flow, account, Result.newBuilder()).run();
  }

  /**
   * Gives a code to the interceptors that deliver codes.
   *
   * @return whether they took it; with none, the response of the flow carries the code
   */
  Future<Boolean> deliver(Delivery delivery) {
    return code.isEmpty() ? Future.succeededFuture(false) : new CodeChain(code, delivery).run().map(true);
  }
}
