package dev.horizon.pyrite.flow;

import dev.horizon.pyrite.CompletionHandler;
import dev.horizon.pyrite.FlowContext;

/**
 * The {@link CompletionHandler}: it does what the declaration of a flow completes with, in its order, and fills in the
 * result. It holds no state, so every instance is equal, and removing a new one removes the one that Pyrite added.
 * When a step fails, the flow fails with its cause, and the interceptors after it do not run.
 */
public final class Completion implements CompletionHandler {

  @Override
  public void handle(FlowContext context) {
    FlowChain chain = (FlowChain) context;
    chain.flowInstance().complete(chain.accountInstance(), chain.result()).onComplete(done -> {
      if (done.succeeded()) {
        context.next();
      } else {
        context.fail(done.cause());
      }
    });
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Completion;
  }

  @Override
  public int hashCode() {
    return Completion.class.hashCode();
  }
}
