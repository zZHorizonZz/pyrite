package dev.horizon.pyrite.authentication.impl;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.authentication.v1.PasskeyAnswer;
import dev.horizon.pyrite.authentication.v1.PasskeyOptions;
import io.vertx.core.Future;
import java.util.Optional;
import java.util.Set;

final class PasskeyEnrolment extends Method<PasskeyAnswer> {

  PasskeyEnrolment() {
    super(PasskeyAnswer.getDefaultInstance());
  }

  @Override
  Future<Optional<Delivery>> prepare(FlowImpl flow) {
    flow.newChallenge();
    return Future.succeededFuture(Optional.empty());
  }

  @Override
  Future<Challenge> challenge(FlowImpl flow) {
    return flow.factors(PasskeyFactor.class).map(found -> Challenge.newBuilder().setMethod(name())
        .setParameters(Any.pack(PasskeyOptions.newBuilder().setOptions(flow.type().webAuthn()
            .creation(flow.accountName(), flow.challenge(), found.stream().map(PasskeyFactor::credentialId).toList())
            .encode()).build()))
        .build());
  }

  @Override
  Future<Optional<Evidence>> evaluate(FlowImpl flow, PasskeyAnswer answer) {
    String attestation = answer.getCredential();
    Optional<WebAuthn.Registration> registration = flow.type().webAuthn().register(attestation, flow.challenge());
    if (registration.isEmpty()) {
      return Future.succeededFuture(Optional.empty());
    }
    return flow.account().compose(account -> account.orElseThrow().enrolPasskey(registration.get()))
        .map(factor -> {
          flow.enrol(factor);
          return Optional.of(new Evidence(Set.of(Evidence.Proof.ENROLLED), true));
        });
  }
}
