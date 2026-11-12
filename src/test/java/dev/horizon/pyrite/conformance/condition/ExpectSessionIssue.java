package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.authentication.v1.Flow;
import dev.horizon.pyrite.authentication.v1.SessionToken;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.session.SessionType;
import dev.horizon.pyrite.session.Token;

/**
 * Checks that the last flow issued a session, and leaves its token and its name.
 */
public record ExpectSessionIssue() implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    Flow flow = environment.get(Value.FLOW);
    assertThat(flow.getResult().getIssuedCount()).as("issued").isEqualTo(1);
    String token = flow.getResult().getIssued(0).unpack(SessionToken.class).getToken();
    assertThat(token).as("token").isNotEmpty();
    environment.put(Value.SESSION_TOKEN, token);
    environment.put(Value.SESSION, SessionType.SESSIONS + Token.parse(token).id());
  }
}
