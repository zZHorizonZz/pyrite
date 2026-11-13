package dev.horizon.pyrite.exchange;

import dev.horizon.pyrite.schema.v1.ExchangeDefinition;
import dev.horizon.pyrite.schema.v1.Stage;

/**
 * How far an exchange got: the stage of its definition that it is at, and what its answers proved so far. Past the
 * last stage, the exchange is done.
 *
 * @param definition the definition that the exchange follows
 * @param stage the index of the stage that the exchange is at, or the number of stages when it is done
 * @param evidence what the answers proved so far
 */
record Progress(ExchangeDefinition definition, int stage, Evidence evidence) {

  /**
   * Adds what an answer proved, and moves past every stage that the evidence then meets.
   *
   * @param gain what the answer proved
   * @return the progress after the answer, at the same stage when the evidence meets none
   */
  Progress pass(Evidence gain) {
    Evidence all = evidence.and(gain);
    int at = stage;
    while (at < definition.getStagesCount() && all.meets(definition.getStages(at))) {
      at++;
    }
    return new Progress(definition, at, all);
  }

  boolean isDone() {
    return stage == definition.getStagesCount();
  }

  /**
   * Gives the stage that the exchange is at.
   *
   * @throws IndexOutOfBoundsException when the exchange is done
   */
  Stage current() {
    return definition.getStages(stage);
  }
}
