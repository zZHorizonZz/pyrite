package dev.horizon.pyrite.exchange;

import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import dev.horizon.pyrite.identity.CredentialType;
import dev.horizon.pyrite.schema.v1.Stage;
import io.vertx.core.Future;
import java.util.Optional;

/**
 * The enrolment of a new password. A password of fewer than 8 or more than 128 characters fails the answer with
 * {@code INVALID_ARGUMENT}.
 */
final class PasswordEnrolment implements Enrolling<PasswordAnswer> {

  private final CredentialType credentials;

  PasswordEnrolment(CredentialType credentials) {
    this.credentials = credentials;
  }

  @Override
  public PasswordAnswer prototype() {
    return PasswordAnswer.getDefaultInstance();
  }

  @Override
  public Evidence proves() {
    return Evidence.ENROLMENT;
  }

  @Override
  public Stage.Factor enrols() {
    return Stage.Factor.PASSWORD;
  }

  @Override
  public Future<Optional<Evidence>> verify(Exchange exchange, PasswordAnswer answer) {
    return exchange.claimant().account().compose(account -> account.isEmpty()
        ? Future.succeededFuture(Optional.empty())
        : credentials.enrolPassword(account.get().name(), answer.getPassword()).map(credential -> {
          exchange.enrol(credential);
          return Optional.of(Evidence.ENROLMENT);
        }));
  }
}
