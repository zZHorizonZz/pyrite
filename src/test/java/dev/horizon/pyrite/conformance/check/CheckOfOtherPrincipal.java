package dev.horizon.pyrite.conformance.check;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;
import static dev.horizon.pyrite.PyriteFixture.CAROL;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Check;
import dev.horizon.pyrite.conformance.condition.ExpectCheckAnswer;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import io.vertx.grpc.common.GrpcStatus;

@Summary("Asking about another principal needs the grant permission.")
final class CheckOfOtherPrincipal extends AccessModule {

  @Override
  protected void run() {
    prepareLedger();
    call(new GrantRelation(ANNE, "acme.dev/Organization:organizations/acme", "member", BOB));
    call(new Check(ANNE, BOB, "acme.dev/Project:projects/ledger", "can_view"));
    callAndContinueOnFailure(new ExpectCheckAnswer(true), Requirement.ACCESS_PRINCIPAL);
    call(new Check(ANNE, CAROL, "acme.dev/Project:projects/ledger", "can_view"));
    callAndContinueOnFailure(new ExpectCheckAnswer(false), Requirement.ACCESS_PRINCIPAL);
    callAndContinueOnFailure(
        new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new Check(BOB, ANNE, "acme.dev/Project:projects/ledger", "can_view")),
        Requirement.ACCESS_PRINCIPAL);
    callAndContinueOnFailure(
        new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new Check(BOB, CAROL, "acme.dev/Project:projects/ledger", "can_view")),
        Requirement.ACCESS_PRINCIPAL);
  }
}
