package dev.horizon.pyrite.flow;

import dev.horizon.pyrite.CodeContext;
import dev.horizon.pyrite.authentication.v1.Delivery;
import io.vertx.core.Handler;
import java.util.List;

/**
 * A run of the interceptors that deliver a one-time code.
 */
final class CodeChain extends Chain<CodeContext> implements CodeContext {

  private final Delivery delivery;

  CodeChain(List<Handler<CodeContext>> interceptors, Delivery delivery) {
    super(interceptors);
    this.delivery = delivery;
  }

  @Override
  public Delivery delivery() {
    return delivery;
  }

  @Override
  CodeContext context() {
    return this;
  }
}
