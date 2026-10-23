package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import io.vertx.core.Future;
import java.util.Optional;

final class PasswordMethod extends Method<PasswordAnswer> {

  private static final String DECOY = "a password that no account has";

  PasswordMethod() {
    super(PasswordAnswer.getDefaultInstance());
  }

  @Override
  Future<Optional<Evidence>> evaluate(FlowImpl flow, PasswordAnswer answer) {
    String password = answer.getPassword();
    return flow.factors(PasswordFactor.class).compose(found -> found.isEmpty()
        ? flow.type().schema().vertx().executeBlocking(() -> PasswordHash.of(DECOY, flow.type().options().argon2()))
            .map(Optional.<Evidence> empty())
        : found.getFirst().matches(password).map(right -> right ? Optional.of(Evidence.of(Evidence.Proof.KNOWLEDGE))
            : Optional.empty()));
  }
}
