package dev.horizon.pyrite.conformance.password;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateAccount;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.SignIn;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A password of another account does not sign in to this one.")
final class PasswordOfOtherAccount extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new CreateAccount("bob", "bob@acme.dev", "another long passphrase"));
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new SignIn(EMAIL, "another long passphrase")),
        Requirement.PASSWORD_ACCOUNT);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new SignIn("bob@acme.dev", PASSWORD)),
        Requirement.PASSWORD_ACCOUNT);
  }
}
