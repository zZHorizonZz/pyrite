package dev.horizon.pyrite.authentication.impl;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.authentication.v1.PasskeyAnswer;
import dev.horizon.pyrite.authentication.v1.PasskeyOptions;
import io.vertx.core.Future;
import java.util.Optional;
import java.util.Set;

final class PasskeyMethod extends Method<PasskeyAnswer> {

  PasskeyMethod() {
    super(PasskeyAnswer.getDefaultInstance());
  }

  @Override
  Future<Optional<Delivery>> prepare(FlowImpl flow) {
    flow.newChallenge();
    return Future.succeededFuture(Optional.empty());
  }

  @Override
  Future<Challenge> challenge(FlowImpl flow) {
    return Future.succeededFuture(Challenge.newBuilder().setMethod(name())
        .setParameters(Any.pack(PasskeyOptions.newBuilder().setOptions(flow.type().webAuthn()
            .request(flow.challenge()).encode()).build()))
        .build());
  }

  @Override
  Future<Optional<Evidence>> evaluate(FlowImpl flow, PasskeyAnswer answer) {
    String assertion = answer.getCredential();
    Optional<String> credential = flow.type().webAuthn().credential(assertion);
    if (credential.isEmpty()) {
      return Future.succeededFuture(Optional.empty());
    }
    return flow.factors(PasskeyFactor.class).compose(found -> found.stream()
        .filter(factor -> factor.credentialId().equals(credential.get())).findFirst()
        .map(factor -> factor.accepts(assertion, flow.challenge())
            .map(asserted -> asserted.map(
                verified -> new Evidence(verified.userVerified() ? Set.of(Evidence.Proof.POSSESSION, Evidence.Proof.INHERENCE)
                    : Set.of(Evidence.Proof.POSSESSION), true))))
        .orElseGet(() -> Future.succeededFuture(Optional.empty())));
  }
}
