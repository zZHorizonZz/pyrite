package dev.horizon.pyrite.flow;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.authentication.v1.PasskeyAnswer;
import dev.horizon.pyrite.authentication.v1.PasskeyOptions;
import dev.horizon.pyrite.identity.PasskeyCredential;
import dev.horizon.pyrite.identity.WebAuthn;
import io.vertx.core.Future;
import java.util.Optional;
import java.util.Set;

/**
 * The enrolment of a new passkey: the creation options hold a new challenge and exclude the passkeys of the account,
 * and a right answer enrols the passkey as pending until the flow activates it.
 */
final class PasskeyEnrolment extends Authentication<PasskeyAnswer> {

  PasskeyEnrolment() {
    super(PasskeyAnswer.getDefaultInstance());
  }

  @Override
  Future<Optional<Delivery>> prepare(Flow flow) {
    flow.newChallenge();
    return Future.succeededFuture(Optional.empty());
  }

  @Override
  Future<Challenge> challenge(Flow flow) {
    return flow.credentials(PasskeyCredential.class).map(found -> Challenge.newBuilder().setMethod(name())
        .setParameters(Any.pack(PasskeyOptions.newBuilder().setOptions(flow.type().webAuthn()
            .creation(flow.accountName(), flow.challenge(), found.stream().map(PasskeyCredential::credentialId).toList())
            .encode()).build()))
        .build());
  }

  @Override
  Future<Optional<Evidence>> evaluate(Flow flow, PasskeyAnswer answer) {
    String attestation = answer.getCredential();
    Optional<WebAuthn.Registration> registration = flow.type().webAuthn().register(attestation, flow.challenge());
    if (registration.isEmpty()) {
      return Future.succeededFuture(Optional.empty());
    }
    return flow.requireAccount().compose(account -> flow.type().credentials().enrolPasskey(account.name(),
        registration.get()))
        .map(factor -> {
          flow.enrol(factor);
          return Optional.of(new Evidence(Set.of(Evidence.Proof.ENROLMENT), true));
        });
  }
}
