package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.authentication.v1.Exchange;
import dev.horizon.pyrite.authentication.v1.TokenSet;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the last exchange issued a session with its tokens, and leaves them and the name of the session.
 */
public record ExpectSessionIssue() implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    Exchange exchange = environment.get(Value.EXCHANGE);
    assertThat(exchange.getResult().getIssuedCount()).as("issued").isEqualTo(1);
    TokenSet tokens = exchange.getResult().getIssued(0).unpack(TokenSet.class);
    assertThat(tokens.getAccessToken()).as("access token").isNotEmpty();
    assertThat(tokens.getRefreshToken()).as("refresh token").isNotEmpty();
    Refresh.keep(environment, tokens);
  }
}
