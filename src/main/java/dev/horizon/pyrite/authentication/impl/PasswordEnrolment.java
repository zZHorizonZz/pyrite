package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import io.vertx.core.Future;
import java.util.Optional;

final class PasswordEnrolment extends Method<PasswordAnswer> {

  PasswordEnrolment() {
    super(PasswordAnswer.getDefaultInstance());
  }

  @Override
  Future<Optional<Evidence>> evaluate(FlowImpl flow, PasswordAnswer answer) {
    String password = answer.getPassword();
    return flow.account().compose(account -> account.isEmpty() ? Future.succeededFuture(Optional.empty())
        : flow.type().schema().vertx()
            .executeBlocking(() -> PasswordHash.of(password, flow.type().options().argon2()))
            .compose(hash -> account.get().enrolPassword(hash))
            .map(factor -> {
              flow.enrol(factor);
              return Optional.of(Evidence.of(Evidence.Proof.ENROLLED));
            }));
  }
}
