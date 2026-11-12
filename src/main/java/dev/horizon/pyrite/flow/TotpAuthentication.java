package dev.horizon.pyrite.flow;

import dev.horizon.pyrite.authentication.v1.TotpAnswer;
import dev.horizon.pyrite.identity.TotpCredential;
import io.vertx.core.Future;
import java.util.Optional;

/**
 * A code of the TOTP secret of the account of the flow, which proves possession, and works once.
 */
final class TotpAuthentication extends Authentication<TotpAnswer> {

  TotpAuthentication() {
    super(TotpAnswer.getDefaultInstance());
  }

  @Override
  Future<Optional<Evidence>> evaluate(Flow flow, TotpAnswer answer) {
    String code = answer.getCode();
    return flow.credentials(TotpCredential.class).compose(found -> {
      Future<Boolean> accepted = Future.succeededFuture(false);
      for (TotpCredential factor : found) {
        accepted = accepted.compose(right -> right ? Future.succeededFuture(true) : factor.accepts(code));
      }
      return accepted.map(right -> right ? Optional.of(Evidence.of(Evidence.Proof.POSSESSION)) : Optional.empty());
    });
  }
}
