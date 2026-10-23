package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.authentication.v1.StartFlowRequest;
import io.vertx.core.Future;

/**
 * The flows of a declaration, kept in the table {@code pyrite_flow} while they run.
 */
public interface FlowType {

  /**
   * Starts a flow of a declared definition. An identifier that no account has gets a flow with the same answer methods,
   * no deliveries, and no right answer.
   *
   * @param request the definition, and the subject or the identifier that its input takes
   * @return the flow, with its first challenges, or with its result when it needs no answer
   */
  Future<dev.horizon.pyrite.authentication.v1.Flow> start(StartFlowRequest request);

  /**
   * Reads a flow that is in progress.
   *
   * @param name the flow's name, such as {@code flows/0b0d...}
   * @return the flow, or a failure with {@code NOT_FOUND}
   */
  Future<Flow> get(String name);
}
