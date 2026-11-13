package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.ContinueExchangeRequest;
import dev.horizon.pyrite.authentication.v1.Exchange;
import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import dev.horizon.pyrite.authentication.v1.StartExchangeRequest;
import dev.horizon.pyrite.authentication.v1.TokenSet;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import java.util.UUID;

/**
 * Signs in with an email address and a password, and leaves the tokens of the session and its name.
 */
public record SignIn(String email, String password) implements Condition {

  /**
   * Signs in with an email address and a password, through the exchange sign-in.
   *
   * @return the tokens that the exchange issued
   */
  public static TokenSet tokens(Environment environment, String email, String password) throws Exception {
    Exchange exchange = environment.pyrite().authentication
        .startExchange(StartExchangeRequest.newBuilder().setDefinition("sign-in")
            .setIdentifierKind("email").setIdentifier(email).build())
        .await(TIMEOUT);
    Exchange done = environment.pyrite().authentication.continueExchange(ContinueExchangeRequest.newBuilder()
        .setExchange(exchange.getName()).setRevision(exchange.getRevision()).setRequestId(UUID.randomUUID().toString())
        .setAnswer(Any.pack(PasswordAnswer.newBuilder().setPassword(password).build())).build()).await(TIMEOUT);
    return done.getResult().getIssued(0).unpack(TokenSet.class);
  }

  @Override
  public void evaluate(Environment environment) throws Exception {
    Refresh.keep(environment, tokens(environment, email, password));
  }
}
