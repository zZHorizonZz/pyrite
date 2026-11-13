package dev.horizon.pyrite.exchange;

import dev.horizon.pyrite.AuthenticationOptions;
import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import dev.horizon.pyrite.authentication.v1.RecoveryCodeAnswer;
import dev.horizon.pyrite.authentication.v1.TotpAnswer;
import dev.horizon.pyrite.identity.CredentialType;
import dev.horizon.pyrite.identity.IdentifierType;
import dev.horizon.pyrite.identity.PasswordCredential;
import dev.horizon.pyrite.identity.Proof;
import dev.horizon.pyrite.identity.RecoveryCodeCredential;
import dev.horizon.pyrite.identity.TotpCredential;
import dev.horizon.pyrite.key.KeyType;
import dev.horizon.pyrite.schema.v1.Stage;
import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The verifiers of exchanges, each built once with the services that it uses, and which of them a stage takes: those
 * whose right answer adds what the stage still lacks, in the order of the challenges. The verifiers of providers are
 * there only when the options name providers.
 */
final class VerifierSet {

  private final List<Verifier<?>> verifiers = new ArrayList<>();
  private final List<Enrolling<?>> enrolments = new ArrayList<>();

  VerifierSet(CredentialType credentials, IdentifierType identifiers, KeyType keys, AuthenticationOptions options) {
    verifiers.add(new CodeVerifier(keys, options));
    verifiers.add(new CredentialVerifier<>(PasswordCredential.class, PasswordAnswer.getDefaultInstance(),
        Evidence.of(Set.of(Proof.KNOWLEDGE), false), credentials::hashDecoy));
    verifiers.add(new CredentialVerifier<>(TotpCredential.class, TotpAnswer.getDefaultInstance(),
        Evidence.of(Set.of(Proof.POSSESSION), false), Future::succeededFuture));
    verifiers.add(new CredentialVerifier<>(RecoveryCodeCredential.class, RecoveryCodeAnswer.getDefaultInstance(),
        Evidence.of(Set.of(Proof.POSSESSION), false), Future::succeededFuture));
    verifiers.add(new PasskeyVerifier(credentials.webAuthn()));
    enrolments.add(new PasswordEnrolment(credentials));
    enrolments.add(new TotpEnrolment(credentials));
    enrolments.add(new PasskeyEnrolment(credentials));
    ProviderSet providers = new ProviderSet(options.getProviders());
    if (!providers.isEmpty()) {
      verifiers.add(new ProviderVerifier(providers, credentials, identifiers));
      enrolments.add(new ProviderEnrolment(providers, credentials));
    }
  }

  /**
   * Gives the verifiers that a stage takes, after the evidence so far. An enrolment takes the verifier of its factor
   * only. Otherwise the stage takes the verifier of a code while it lacks the proof of its identifier; and a passkey
   * alone while it lacks phishing resistance, or, while it lacks factors, each verifier that proves a factor that the
   * evidence does not hold.
   *
   * @param stage the stage that the exchange is at
   * @param evidence what the answers proved so far
   * @return the verifiers, in the order of the challenges
   */
  List<Verifier<?>> at(Stage stage, Evidence evidence) {
    if (stage.getEnrol() != Stage.Factor.FACTOR_UNSPECIFIED) {
      return enrolments.stream().filter(enrolment -> enrolment.enrols() == stage.getEnrol())
          .<Verifier<?>> map(enrolment -> enrolment).toList();
    }
    boolean identifier = stage.getIdentifier() && !evidence.identifier();
    boolean phishingResistant = stage.getPhishingResistant() && !evidence.phishingResistant();
    boolean factors = !phishingResistant && evidence.factors().size() < stage.getFactors();
    List<Verifier<?>> taken = new ArrayList<>();
    for (Verifier<?> verifier : verifiers) {
      Evidence proves = verifier.proves();
      if (identifier && proves.identifier() || phishingResistant && proves.phishingResistant()
          || factors && !evidence.factors().containsAll(proves.factors())) {
        taken.add(verifier);
      }
    }
    return taken;
  }
}
