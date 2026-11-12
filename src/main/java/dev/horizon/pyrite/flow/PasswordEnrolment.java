package dev.horizon.pyrite.flow;

import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import io.vertx.core.Future;
import java.util.Optional;

/**
 * The enrolment of a new password, which enrols it as pending until the flow activates it. A password of fewer than 8
 * or more than 128 characters fails the answer with {@code INVALID_ARGUMENT}.
 */
final class PasswordEnrolment extends Authentication<PasswordAnswer> {

  PasswordEnrolment() {
    super(PasswordAnswer.getDefaultInstance());
  }

  @Override
  Future<Optional<Evidence>> evaluate(Flow flow, PasswordAnswer answer) {
    String password = answer.getPassword();
    return flow.account().compose(account -> account.isEmpty() ? Future.succeededFuture(Optional.empty())
        : flow.type().credentials().enrolPassword(account.get().name(), password).map(credential -> {
          flow.enrol(credential);
          return Optional.of(Evidence.of(Evidence.Proof.ENROLMENT));
        }));
  }
}
