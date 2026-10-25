package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.authentication.v1.Result;
import io.vertx.core.Future;

final class MarkVerified implements Effect {

  @Override
  public Future<Void> apply(FlowImpl flow, AccountImpl account, Result.Builder result) {
    return flow.identifier().compose(identifier -> identifier.orElseThrow().verify()).map(verified -> {
      result.addIdentifiers(verified.message());
      return null;
    });
  }
}
