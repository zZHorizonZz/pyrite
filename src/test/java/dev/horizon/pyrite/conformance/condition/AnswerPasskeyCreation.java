package dev.horizon.pyrite.conformance.condition;

import com.webauthn4j.data.AttestationConveyancePreference;
import com.webauthn4j.data.AuthenticatorAttestationResponse;
import com.webauthn4j.data.AuthenticatorSelectionCriteria;
import com.webauthn4j.data.PublicKeyCredential;
import com.webauthn4j.data.PublicKeyCredentialCreationOptions;
import com.webauthn4j.data.PublicKeyCredentialParameters;
import com.webauthn4j.data.PublicKeyCredentialRpEntity;
import com.webauthn4j.data.PublicKeyCredentialType;
import com.webauthn4j.data.PublicKeyCredentialUserEntity;
import com.webauthn4j.data.ResidentKeyRequirement;
import com.webauthn4j.data.UserVerificationRequirement;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.data.extension.client.RegistrationExtensionClientOutput;
import dev.horizon.pyrite.authentication.v1.PasskeyAnswer;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;

/**
 * Makes a passkey with the browser, for the creation options of the last flow, and answers the flow with it as a
 * browser gives it. The authenticator signs with ES256, which the options offer first.
 */
public record AnswerPasskeyCreation() implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    JsonObject options = PasskeyOptions.of(environment.get(Value.FLOW));
    JsonObject rp = options.getJsonObject("rp");
    JsonObject user = options.getJsonObject("user");
    PublicKeyCredential<AuthenticatorAttestationResponse, RegistrationExtensionClientOutput> credential = environment
        .get(Value.PASSKEY_CLIENT).create(new PublicKeyCredentialCreationOptions(new PublicKeyCredentialRpEntity(
            rp.getString("id"), rp.getString("name")),
            new PublicKeyCredentialUserEntity(PasskeyOptions.decode(user
                .getString("id")), user.getString("name"), user.getString("displayName")),
            new DefaultChallenge(PasskeyOptions.decode(options.getString("challenge"))), List.of(
                new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.ES256)),
            options.getLong("timeout"), List.of(), new AuthenticatorSelectionCriteria(null,
                ResidentKeyRequirement.PREFERRED, UserVerificationRequirement.PREFERRED),
            AttestationConveyancePreference.NONE, null));
    String id = PasskeyOptions.encode(credential.getRawId());
    JsonObject answer = new JsonObject().put("id", id).put("rawId", id).put("type", "public-key")
        .put("response", new JsonObject()
            .put("clientDataJSON", PasskeyOptions.encode(credential.getResponse().getClientDataJSON()))
            .put("attestationObject", PasskeyOptions.encode(credential.getResponse().getAttestationObject()))
            .put("transports", new JsonArray()))
        .put("clientExtensionResults", new JsonObject());
    environment.answer(PasskeyAnswer.newBuilder().setCredential(answer.encode()).build());
  }
}
