package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.authentication.v1.RefreshSessionRequest;
import dev.horizon.pyrite.authentication.v1.TokenSet;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Trades the refresh token of the last session for new tokens, and leaves them, with the refresh token that it traded.
 */
public record Refresh() implements Condition {

  /**
   * Trades a refresh token for new tokens.
   */
  public static TokenSet tokens(Environment environment, String refreshToken) throws Exception {
    return environment.pyrite().authentication.refreshSession(RefreshSessionRequest.newBuilder()
        .setRefreshToken(refreshToken).build()).await(TIMEOUT);
  }

  /**
   * Leaves the tokens of a session, and the name of the session.
   */
  public static void keep(Environment environment, TokenSet tokens) {
    environment.put(Value.ACCESS_TOKEN, tokens.getAccessToken());
    environment.put(Value.REFRESH_TOKEN, tokens.getRefreshToken());
    environment.put(Value.SESSION, tokens.getSession());
  }

  @Override
  public void evaluate(Environment environment) throws Exception {
    String spent = environment.get(Value.REFRESH_TOKEN);
    TokenSet tokens = tokens(environment, spent);
    environment.put(Value.SPENT_REFRESH_TOKEN, spent);
    keep(environment, tokens);
  }
}
