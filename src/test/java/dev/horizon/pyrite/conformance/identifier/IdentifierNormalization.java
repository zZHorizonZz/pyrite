package dev.horizon.pyrite.conformance.identifier;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AddIdentifier;
import dev.horizon.pyrite.conformance.condition.ExpectIdentifier;
import java.util.List;

@Summary("An identifier is stored as its kind normalizes it, and unverified.")
final class IdentifierNormalization extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    for (List<String> identifier : List.of(List.of("email", " Anne.Smith@Example.COM ", "anne.smith@example.com"),
        List.of("phone", "+420 777-123-456", "+420777123456"), List.of("username", " Anne_Smith ", "anne_smith"))) {
      callAndContinueOnFailure(new AddIdentifier(ANNE, "accounts/anne", identifier.get(0), identifier.get(1)),
          Requirement.IDENTIFIER_FORMAT);
      callAndContinueOnFailure(new ExpectIdentifier(identifier.get(0), identifier.get(2), "accounts/anne"),
          Requirement.IDENTIFIER_FORMAT);
    }
  }
}
