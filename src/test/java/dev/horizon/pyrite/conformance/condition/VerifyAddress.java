package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.authentication.v1.CodeAnswer;
import dev.horizon.pyrite.authentication.v1.Exchange;
import dev.horizon.pyrite.authentication.v1.StartExchangeRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;

/**
 * Verifies an email address of a subject's account, with the code that Pyrite sends to it.
 */
public record VerifyAddress(String subject, String email) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    Exchange exchange = environment.start(StartExchangeRequest.newBuilder().setSubject(subject).setDefinition("verify")
        .setIdentifierKind("email").setIdentifier(email).build());
    environment.answer(CodeAnswer.newBuilder().setCode(environment.code(exchange).getCode()).build());
  }
}
