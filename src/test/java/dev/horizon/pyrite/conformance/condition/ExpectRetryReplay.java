package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.ContinueExchangeRequest;
import dev.horizon.pyrite.authentication.v1.Exchange;
import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.UUID;

/**
 * Answers the last exchange with a password twice, with the same request id, and checks that the second gets the
 * response of the first.
 */
public record ExpectRetryReplay(String password) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    Exchange exchange = environment.get(Value.EXCHANGE);
    ContinueExchangeRequest request = ContinueExchangeRequest.newBuilder().setExchange(exchange.getName())
        .setRevision(exchange.getRevision()).setRequestId(UUID.randomUUID().toString()).setAnswer(Any.pack(
            PasswordAnswer.newBuilder().setPassword(password).build()))
        .build();
    Exchange first = environment.continueExchange(request);
    assertThat(environment.continueExchange(request)).isEqualTo(first);
  }
}
