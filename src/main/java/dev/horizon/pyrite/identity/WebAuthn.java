package dev.horizon.pyrite.identity;

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
import dev.horizon.pyrite.RelyingPartyOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The WebAuthn relying party of the options of authentication: it gives the options of passkeys as the JSON that
 * {@code PublicKeyCredential.parseCreationOptionsFromJSON} and {@code parseRequestOptionsFromJSON} of a browser take,
 * and verifies the JSON of the credentials that the browser gives back, with webauthn4j.
 *
 * <p>
 * The options name the algorithms ES256, EdDSA and RS256, a timeout of five minutes, no attestation, and prefer a
 * discoverable passkey and user verification. A credential verifies only from an origin of the relying party, for its
 * id, with the user present, and for the challenge of the exchange that gave the options.
 */
public final class WebAuthn {

  /**
   * A passkey that an attestation registers.
   *
   * @param credential the attested credential data, with the public key, as webauthn4j encodes it
   * @param credentialId the id of the passkey, in base64url without padding
   * @param counter the signature counter of the authenticator
   * @param userVerified whether the authenticator verified the user
   */
  public record Registration(byte[] credential, String credentialId, long counter, boolean userVerified) {
  }

  /**
   * An assertion that verifies.
   *
   * @param counter the new signature counter of the authenticator
   * @param userVerified whether the authenticator verified the user, which proves inherence
   */
  public record Assertion(long counter, boolean userVerified) {
  }

  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
  private static final List<COSEAlgorithmIdentifier> ALGORITHMS = List.of(COSEAlgorithmIdentifier.ES256,
      COSEAlgorithmIdentifier.EdDSA, COSEAlgorithmIdentifier.RS256);
  private static final long TIMEOUT = 300_000;

  private final RelyingPartyOptions party;
  private final WebAuthnManager manager = WebAuthnManager.createNonStrictWebAuthnManager();
  private final AttestedCredentialDataConverter converter = new AttestedCredentialDataConverter(new ObjectConverter());

  WebAuthn(RelyingPartyOptions party) {
    this.party = party;
  }

  /**
   * Gives the creation options of a passkey of an account.
   *
   * @param account the name of the account, which is the name of the user and, in base64url, its id
   * @param challenge the challenge of the exchange
   * @param existing the ids of the passkeys that the account has, which the options exclude
   * @return the options, as {@code PublicKeyCredentialCreationOptionsJSON}
   */
  public JsonObject creation(String account, byte[] challenge, List<String> existing) {
    JsonArray parameters = new JsonArray();
    for (COSEAlgorithmIdentifier algorithm : ALGORITHMS) {
      parameters.add(new JsonObject().put("type", "public-key").put("alg", algorithm.getValue()));
    }
    JsonArray excluded = new JsonArray();
    existing.forEach(id -> excluded.add(new JsonObject().put("type", "public-key").put("id", id)));
    return new JsonObject()
        .put("challenge", ENCODER.encodeToString(challenge))
        .put("rp", new JsonObject().put("id", party.getId()).put("name", party.getName()))
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

  /**
   * Gives the request options of a sign-in. They list no passkeys, so the browser offers the discoverable passkeys of
   * the relying party.
   *
   * @param challenge the challenge of the exchange
   * @return the options, as {@code PublicKeyCredentialRequestOptionsJSON}
   */
  public JsonObject request(byte[] challenge) {
    return new JsonObject()
        .put("challenge", ENCODER.encodeToString(challenge))
        .put("rpId", party.getId())
        .put("timeout", TIMEOUT)
        .put("allowCredentials", new JsonArray())
        .put("userVerification", "preferred");
  }

  /**
   * Verifies the attestation of a new passkey.
   *
   * @param credential the JSON of the credential that {@code navigator.credentials.create} gave
   * @param challenge the challenge of the exchange, which the creation options held
   * @return the passkey, or nothing if the credential does not parse or does not verify
   */
  public Optional<Registration> register(String credential, byte[] challenge) {
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

  /**
   * Reads the id of the passkey that an assertion names, without verifying it, to find the passkey to verify it with.
   *
   * @param assertion the JSON of the credential that {@code navigator.credentials.get} gave
   * @return the id, in base64url without padding, or nothing if the assertion does not parse
   */
  public Optional<String> credential(String assertion) {
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
    Set<Origin> origins = party.getOrigins().stream().map(Origin::new).collect(Collectors.toSet());
    return new ServerProperty(origins, party.getId(), new DefaultChallenge(challenge));
  }
}
