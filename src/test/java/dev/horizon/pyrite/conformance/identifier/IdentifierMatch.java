package dev.horizon.pyrite.conformance.identifier;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AddIdentifier;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;

@Summary("An identifier that its kind's match refuses is refused.")
final class IdentifierMatch extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    for (List<String> identifier : List.of(List.of("email", "anne"), List.of("email", "anne@"),
        List.of("email", "@acme.dev"), List.of("email", "anne@acme"), List.of("email", "an ne@acme.dev"),
        List.of("email", "anne@@acme.dev"), List.of("phone", "777123456"), List.of("phone", "+0777123456"),
        List.of("phone", "+42"), List.of("phone", "+420abc"), List.of("username", "an"),
        List.of("username", "-anne"), List.of("username", "an ne"), List.of("username", "anne!"))) {
      callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new AddIdentifier(ANNE, "accounts/anne",
          identifier.get(0), identifier.get(1))), Requirement.IDENTIFIER_FORMAT);
    }
  }
}
