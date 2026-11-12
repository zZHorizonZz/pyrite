package dev.horizon.pyrite.conformance.condition;

import com.webauthn4j.data.AuthenticatorAssertionResponse;
import com.webauthn4j.data.PublicKeyCredential;
import com.webauthn4j.data.PublicKeyCredentialRequestOptions;
import com.webauthn4j.data.UserVerificationRequirement;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.data.extension.client.AuthenticationExtensionClientOutput;
import dev.horizon.pyrite.authentication.v1.Flow;
import dev.horizon.pyrite.authentication.v1.PasskeyAnswer;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import io.vertx.core.json.JsonObject;
import java.util.List;

/**
 * Signs with the passkey of the browser, for the request options of a flow, and answers the last flow with it as a
 * browser gives it. The options are the last flow's, or another's to answer one flow with another's challenge.
 */
public record AnswerPasskeyRequest(Value<Flow> optionsOf) implements Condition {

  public AnswerPasskeyRequest() {
    this(Value.FLOW);
  }

  @Override
  public void evaluate(Environment environment) throws Exception {
    JsonObject options = PasskeyOptions.of(environment.get(optionsOf));
    PublicKeyCredential<AuthenticatorAssertionResponse, AuthenticationExtensionClientOutput> credential = environment
        .get(Value.PASSKEY_CLIENT).get(new PublicKeyCredentialRequestOptions(new DefaultChallenge(PasskeyOptions
            .decode(options.getString("challenge"))), options.getLong("timeout"), options.getString("rpId"),
            List.of(), UserVerificationRequirement.PREFERRED, null));
    String id = PasskeyOptions.encode(credential.getRawId());
    JsonObject response = new JsonObject()
        .put("clientDataJSON", PasskeyOptions.encode(credential.getResponse().getClientDataJSON()))
        .put("authenticatorData", PasskeyOptions.encode(credential.getResponse().getAuthenticatorData()))
        .put("signature", PasskeyOptions.encode(credential.getResponse().getSignature()));
    if (credential.getResponse().getUserHandle() != null) {
      response.put("userHandle", PasskeyOptions.encode(credential.getResponse().getUserHandle()));
    }
    JsonObject answer = new JsonObject().put("id", id).put("rawId", id).put("type", "public-key")
        .put("response", response).put("clientExtensionResults", new JsonObject());
    environment.answer(PasskeyAnswer.newBuilder().setCredential(answer.encode()).build());
  }
}
