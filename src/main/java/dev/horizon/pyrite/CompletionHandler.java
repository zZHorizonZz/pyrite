package dev.horizon.pyrite;

import dev.horizon.pyrite.flow.Completion;
import io.vertx.core.Handler;

/**
 * The interceptor of the end of a flow that does what the declaration of the flow completes with, in its order: it
 * issues a session, ends the other sessions, verifies the identifier, or activates the factor that the flow enrolled.
 * The end of every flow starts with it, before the interceptors that an application adds.
 *
 * <pre>{@code
 * pyrite.removeFlowEndInterceptor(CompletionHandler.create());
 * pyrite.addFlowEndInterceptor(context -> approvals.ask(context).onComplete(approved -> {
 *   if (approved.succeeded()) {
 *     CompletionHandler.create().handle(context);
 *   } else {
 *     context.fail(approved.cause());
 *   }
 * }));
 * }</pre>
 *
 * <p>
 * Every instance is equal to every other, so {@code removeFlowEndInterceptor(CompletionHandler.create())} removes the
 * one that Pyrite added. Without it, a flow ends without its declared steps.
 */
public interface CompletionHandler extends Handler<FlowContext> {

  /**
   * Creates the interceptor.
   *
   * @return the interceptor, equal to every other
   */
  static CompletionHandler create() {
    return new Completion();
  }
}
