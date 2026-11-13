package dev.horizon.pyrite.conformance.condition;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.ContinueExchangeRequest;
import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import java.util.UUID;

/**
 * Answers the exchange of a name, which may be no exchange's, with a password, at revision 1.
 */
public record AnswerExchangeByName(String exchange, String password) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.continueExchange(ContinueExchangeRequest.newBuilder().setExchange(exchange).setRevision(1)
        .setRequestId(UUID.randomUUID().toString()).setAnswer(Any.pack(PasswordAnswer.newBuilder()
            .setPassword(password).build()))
        .build());
  }
}
