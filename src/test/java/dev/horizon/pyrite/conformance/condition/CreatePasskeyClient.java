package dev.horizon.pyrite.conformance.condition;

import com.webauthn4j.data.client.Origin;
import com.webauthn4j.test.authenticator.webauthn.NoneAttestationAuthenticator;
import com.webauthn4j.test.authenticator.webauthn.WebAuthnAuthenticatorAdaptor;
import com.webauthn4j.test.client.ClientPlatform;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Makes a browser at an origin, with an authenticator that keeps the passkeys that it makes, and leaves it.
 */
public record CreatePasskeyClient(String origin) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    environment.put(Value.PASSKEY_CLIENT, new ClientPlatform(new Origin(origin), new WebAuthnAuthenticatorAdaptor(
        new NoneAttestationAuthenticator())));
  }
}
