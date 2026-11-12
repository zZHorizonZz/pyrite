package dev.horizon.pyrite.session;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.SessionToken;

/**
 * A session that a flow issues, with its token, which Pyrite gives out only once, in the result of the flow.
 *
 * @param session the session
 * @param token the token that authenticates it
 */
public record NewSession(Session session, Token token) {

  /**
   * Gives the token as the result of a flow gives it out, in {@code issued}.
   *
   * @return the token, the name of the session and its expiry, as a {@code SessionToken} packed in an {@code Any}
   */
  public Any toAny() {
    return Any.pack(SessionToken.newBuilder().setToken(token.toString()).setSession(session.name())
        .setExpireTime(session.message().getExpireTime()).build());
  }
}
