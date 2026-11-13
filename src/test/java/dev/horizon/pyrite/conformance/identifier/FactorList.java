package dev.horizon.pyrite.conformance.identifier;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.authentication.v1.Factor;
import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.AnswerTotp;
import dev.horizon.pyrite.conformance.condition.CreateAccountOfKind;
import dev.horizon.pyrite.conformance.condition.ExpectFactorKinds;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.KeepTotpSecret;
import dev.horizon.pyrite.conformance.condition.ListFactors;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;

@Summary("An account lists its own active factors to itself only, without a pending enrolment.")
final class FactorList extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new CreateAccountOfKind("mallory", "HUMAN"));
    call(new StartExchange(ANNE, "enrol-totp", ""));
    call(new KeepTotpSecret(Value.TOTP_SECRET));
    callAndContinueOnFailure(new ExpectFactorKinds(ANNE, "accounts/anne", List.of(Factor.Kind.PASSWORD)),
        Requirement.FACTOR_LIST);
    call(new AnswerTotp(Value.TOTP_SECRET, -1));
    callAndContinueOnFailure(new ExpectFactorKinds(ANNE, "accounts/anne", List.of(Factor.Kind.PASSWORD,
        Factor.Kind.TOTP)), Requirement.FACTOR_LIST);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new ListFactors("user:mallory",
        "accounts/anne")), Requirement.FACTOR_LIST);
  }
}
