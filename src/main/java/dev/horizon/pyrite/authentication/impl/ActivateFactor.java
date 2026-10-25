package dev.horizon.pyrite.authentication.impl;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.RecoveryCodes;
import dev.horizon.pyrite.authentication.v1.Result;
import io.vertx.core.Future;

final class ActivateFactor implements Effect {

  @Override
  public Future<Void> apply(FlowImpl flow, AccountImpl account, Result.Builder result) {
    if (flow.hasEnrolment()) {
      return flow.enrolment(Factor.class).compose(Factor::activate).map(active -> {
        result.addFactors(active.message());
        return null;
      });
    }
    return account.createRecoveryCodes().compose(created -> created.activate().map(active -> {
      result.addFactors(active.message()).addIssued(Any.pack(RecoveryCodes.newBuilder()
          .addAllCodes(created.codes().orElseThrow()).build()));
      return null;
    }));
  }
}
