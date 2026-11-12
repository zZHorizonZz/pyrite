package dev.horizon.pyrite.conformance.code;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateAccount;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.StartFlow;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An account verifies only an address of its own.")
final class AddressOfOtherAccount extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new CreateAccount("bob", "bob@acme.dev", "another long passphrase"));
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.NOT_FOUND, new StartFlow(ANNE, "verify", "bob@acme.dev")),
        Requirement.CODE_OWNER);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.NOT_FOUND, new StartFlow(ANNE, "verify",
        "nobody@acme.dev")), Requirement.CODE_OWNER);
  }
}
