package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.authentication.v1.ContinueFlowRequest;
import io.vertx.core.Future;

/**
 * A flow in progress: one run of a flow definition, with the stages that are still to be proven.
 */
public interface Flow {

  /**
   * Takes an answer to one of the flow's challenges. A wrong answer fails with {@code PERMISSION_DENIED} and counts
   * against {@code maxAnswers}. A retry with the same request id gives the first result.
   *
   * @param request the flow's revision, the request id and the answer
   * @return the flow, with its next challenges or its result
   */
  Future<dev.horizon.pyrite.authentication.v1.Flow> answer(ContinueFlowRequest request);
}
