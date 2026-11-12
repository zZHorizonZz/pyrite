package dev.horizon.pyrite.flow;

import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import dev.horizon.pyrite.identity.PasswordCredential;
import io.vertx.core.Future;
import java.util.Optional;

/**
 * The password of the account of the flow, which proves knowledge. A flow of no account, or of one without a
 * password, hashes a password all the same, so that its answers take as long as those of any other.
 */
final class PasswordAuthentication extends Authentication<PasswordAnswer> {

  PasswordAuthentication() {
    super(PasswordAnswer.getDefaultInstance());
  }

  @Override
  Future<Optional<Evidence>> evaluate(Flow flow, PasswordAnswer answer) {
    String password = answer.getPassword();
    return flow.credentials(PasswordCredential.class).compose(credentials -> credentials.isEmpty()
        ? flow.type().credentials().hashDecoy().map(Optional.<Evidence> empty())
        : credentials.getFirst().matches(password).map(right -> right ? Optional.of(Evidence.of(
            Evidence.Proof.KNOWLEDGE)) : Optional.empty()));
  }
}
