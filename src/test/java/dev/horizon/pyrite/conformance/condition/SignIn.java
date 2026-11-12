package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.ContinueFlowRequest;
import dev.horizon.pyrite.authentication.v1.Flow;
import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import dev.horizon.pyrite.authentication.v1.SessionToken;
import dev.horizon.pyrite.authentication.v1.StartFlowRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.session.SessionType;
import dev.horizon.pyrite.session.Token;
import java.util.UUID;

/**
 * Signs in with an email address and a password, and leaves the token of the session and its name.
 */
public record SignIn(String email, String password) implements Condition {

  /**
   * Signs in with an email address and a password, through the flow sign-in.
   *
   * @return the session token that the flow issued
   */
  public static String token(Environment environment, String email, String password) throws Exception {
    Flow flow = environment.pyrite().authentication.startFlow(StartFlowRequest.newBuilder().setDefinition("sign-in")
        .setIdentifierKind("email").setIdentifier(email).build()).await(TIMEOUT);
    Flow done = environment.pyrite().authentication.continueFlow(ContinueFlowRequest.newBuilder()
        .setFlow(flow.getName()).setRevision(flow.getRevision()).setRequestId(UUID.randomUUID().toString())
        .setAnswer(Any.pack(PasswordAnswer.newBuilder().setPassword(password).build())).build()).await(TIMEOUT);
    return done.getResult().getIssued(0).unpack(SessionToken.class).getToken();
  }

  @Override
  public void evaluate(Environment environment) throws Exception {
    String token = token(environment, email, password);
    environment.put(Value.SESSION_TOKEN, token);
    environment.put(Value.SESSION, SessionType.SESSIONS + Token.parse(token).id());
  }
}
