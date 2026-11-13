package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.FakeProvider;
import dev.horizon.pyrite.authentication.v1.ProviderAnswer;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Answers the last exchange with the code that the provider sent to the callback last, which an earlier exchange
 * traded, and with the state of the last exchange, as an attacker who caught the code would.
 */
public record AnswerSpentProviderCode(String provider) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    String query = URI.create(SignInAtProvider.authorizationUrl(environment, provider)).getRawQuery();
    String state = "";
    for (String pair : query.split("&")) {
      if (pair.startsWith("state=")) {
        state = URLDecoder.decode(pair.substring("state=".length()), StandardCharsets.UTF_8);
      }
    }
    FakeProvider.Callback spent = environment.get(Value.PROVIDER_CALLBACK);
    environment.answer(ProviderAnswer.newBuilder().setProvider(provider).setCode(spent.code()).setState(state)
        .build());
  }
}
