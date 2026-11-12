package dev.horizon.pyrite.flow;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.authentication.v1.PasskeyAnswer;
import dev.horizon.pyrite.authentication.v1.PasskeyOptions;
import dev.horizon.pyrite.identity.PasskeyCredential;
import io.vertx.core.Future;
import java.util.Optional;
import java.util.Set;

/**
 * A passkey of the account of the flow, which proves possession, inherence too when the authenticator verified the
 * user, and is phishing-resistant. The flow makes a new challenge when it reaches the stage, and the request options
 * hold it.
 */
final class PasskeyAuthentication extends Authentication<PasskeyAnswer> {

  PasskeyAuthentication() {
    super(PasskeyAnswer.getDefaultInstance());
  }

  @Override
  Future<Optional<Delivery>> prepare(Flow flow) {
    flow.newChallenge();
    return Future.succeededFuture(Optional.empty());
  }

  @Override
  Future<Challenge> challenge(Flow flow) {
    return Future.succeededFuture(Challenge.newBuilder().setMethod(name())
        .setParameters(Any.pack(PasskeyOptions.newBuilder().setOptions(flow.type().webAuthn()
            .request(flow.challenge()).encode()).build()))
        .build());
  }

  @Override
  Future<Optional<Evidence>> evaluate(Flow flow, PasskeyAnswer answer) {
    String assertion = answer.getCredential();
    Optional<String> credential = flow.type().webAuthn().credential(assertion);
    if (credential.isEmpty()) {
      return Future.succeededFuture(Optional.empty());
    }
    return flow.credentials(PasskeyCredential.class).compose(found -> found.stream()
        .filter(factor -> factor.credentialId().equals(credential.get())).findFirst()
        .map(factor -> factor.accepts(assertion, flow.challenge())
            .map(asserted -> asserted.map(
                verified -> new Evidence(verified.userVerified() ? Set.of(Evidence.Proof.POSSESSION, Evidence.Proof.INHERENCE)
                    : Set.of(Evidence.Proof.POSSESSION), true))))
        .orElseGet(() -> Future.succeededFuture(Optional.empty())));
  }
}
