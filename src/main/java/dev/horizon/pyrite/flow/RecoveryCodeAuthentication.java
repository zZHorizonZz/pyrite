package dev.horizon.pyrite.flow;

import dev.horizon.pyrite.authentication.v1.RecoveryCodeAnswer;
import dev.horizon.pyrite.identity.RecoveryCodeCredential;
import io.vertx.core.Future;
import java.util.Optional;

/**
 * A recovery code of the account of the flow, which proves possession, and works once.
 */
final class RecoveryCodeAuthentication extends Authentication<RecoveryCodeAnswer> {

  RecoveryCodeAuthentication() {
    super(RecoveryCodeAnswer.getDefaultInstance());
  }

  @Override
  Future<Optional<Evidence>> evaluate(Flow flow, RecoveryCodeAnswer answer) {
    String code = answer.getCode();
    return flow.credentials(RecoveryCodeCredential.class).compose(found -> found.isEmpty()
        ? Future.succeededFuture(Optional.empty())
        : found.getFirst().use(code).map(right -> right ? Optional.of(Evidence.of(Evidence.Proof.POSSESSION))
            : Optional.empty()));
  }
}
