package dev.horizon.pyrite.authentication.impl;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.data.AuthenticationData;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.PublicKeyCredentialParameters;
import com.webauthn4j.data.PublicKeyCredentialType;
import com.webauthn4j.data.RegistrationData;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import dev.horizon.pyrite.PyriteServiceOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class WebAuthn {

  record Registration(byte[] credential, String credentialId, long counter, boolean userVerified) {
  }

  record Assertion(long counter, boolean userVerified) {
  }

  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
  private static final List<COSEAlgorithmIdentifier> ALGORITHMS = List.of(COSEAlgorithmIdentifier.ES256,
      COSEAlgorithmIdentifier.EdDSA, COSEAlgorithmIdentifier.RS256);
  private static final long TIMEOUT = 300_000;

  private final PyriteServiceOptions.RelyingParty party;
  private final WebAuthnManager manager = WebAuthnManager.createNonStrictWebAuthnManager();
  private final AttestedCredentialDataConverter converter = new AttestedCredentialDataConverter(new ObjectConverter());

  WebAuthn(PyriteServiceOptions.RelyingParty party) {
    this.party = party;
  }

  JsonObject creation(String account, byte[] challenge, List<String> existing) {
    JsonArray parameters = new JsonArray();
    for (COSEAlgorithmIdentifier algorithm : ALGORITHMS) {
      parameters.add(new JsonObject().put("type", "public-key").put("alg", algorithm.getValue()));
    }
    JsonArray excluded = new JsonArray();
    existing.forEach(id -> excluded.add(new JsonObject().put("type", "public-key").put("id", id)));
    return new JsonObject()
        .put("challenge", ENCODER.encodeToString(challenge))
        .put("rp", new JsonObject().put("id", party.id()).put("name", party.name()))
        .put("user", new JsonObject()
            .put("id", ENCODER.encodeToString(account.getBytes(StandardCharsets.UTF_8)))
            .put("name", account)
            .put("displayName", account))
        .put("pubKeyCredParams", parameters)
        .put("timeout", TIMEOUT)
        .put("excludeCredentials", excluded)
        .put("authenticatorSelection", new JsonObject().put("residentKey", "preferred")
            .put("userVerification", "preferred"))
        .put("attestation", "none");
  }

  JsonObject request(byte[] challenge) {
    return new JsonObject()
        .put("challenge", ENCODER.encodeToString(challenge))
        .put("rpId", party.id())
        .put("timeout", TIMEOUT)
        .put("allowCredentials", new JsonArray())
        .put("userVerification", "preferred");
  }

  Optional<Registration> register(String credential, byte[] challenge) {
    try {
      RegistrationData data = manager.verifyRegistrationResponseJSON(credential, new RegistrationParameters(
          server(challenge), ALGORITHMS.stream()
              .map(algorithm -> new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, algorithm))
              .toList(),
          false, true));
      AuthenticatorData<?> authenticator = data.getAttestationObject().getAuthenticatorData();
      AttestedCredentialData attested = authenticator.getAttestedCredentialData();
      return Optional.of(new Registration(converter.convert(attested), ENCODER.encodeToString(attested
          .getCredentialId()), authenticator.getSignCount(), authenticator.isFlagUV()));
    } catch (RuntimeException invalid) {
      return Optional.empty();
    }
  }

  Optional<String> credential(String assertion) {
    try {
      return Optional.of(ENCODER.encodeToString(manager.parseAuthenticationResponseJSON(assertion)
          .getCredentialId()));
    } catch (RuntimeException invalid) {
      return Optional.empty();
    }
  }

  Optional<Assertion> verify(String assertion, byte[] challenge, byte[] credential, String credentialId,
      long counter, boolean userVerified) {
    try {
      CredentialRecordImpl record = new CredentialRecordImpl(new NoneAttestationStatement(), userVerified, false,
          false, counter, converter.convert(credential), null, null, null, null);
      AuthenticationData data = manager.verifyAuthenticationResponseJSON(assertion, new AuthenticationParameters(
          server(challenge), record, List.of(DECODER.decode(credentialId)), false, true));
      return Optional.of(new Assertion(data.getAuthenticatorData().getSignCount(),
          data.getAuthenticatorData().isFlagUV()));
    } catch (RuntimeException invalid) {
      return Optional.empty();
    }
  }

  private ServerProperty server(byte[] challenge) {
    Set<Origin> origins = party.origins().stream().map(Origin::new).collect(Collectors.toSet());
    return new ServerProperty(origins, party.id(), new DefaultChallenge(challenge));
  }
}
