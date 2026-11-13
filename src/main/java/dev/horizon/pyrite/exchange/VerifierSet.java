package dev.horizon.pyrite.exchange;

import dev.horizon.pyrite.AuthenticationOptions;
import dev.horizon.pyrite.identity.FactorService;
import dev.horizon.pyrite.identity.IdentifierService;
import dev.horizon.pyrite.key.KeyService;
import dev.horizon.pyrite.schema.v1.Stage;
import java.util.ArrayList;
import java.util.List;

/**
 * The verifiers of exchanges, each built once with the services that it uses, and which of them a stage takes: those
 * whose right answer adds what the stage still lacks, in the order of the challenges. The verifiers of providers are
 * there only when the options name providers.
 */
final class VerifierSet {

  private final List<Verifier<?>> verifiers = new ArrayList<>();
  private final List<Enrolling<?>> enrolments = new ArrayList<>();

  VerifierSet(FactorService factors, IdentifierService identifiers, KeyService keys, AuthenticationOptions options) {
    verifiers.add(new CodeVerifier(keys, options));
    verifiers.add(new PasswordVerifier(factors));
    verifiers.add(new TotpVerifier(factors));
    verifiers.add(new RecoveryCodeVerifier(factors));
    verifiers.add(new PasskeyVerifier(factors));
    enrolments.add(new PasswordEnrolment(factors));
    enrolments.add(new TotpEnrolment(factors));
    enrolments.add(new PasskeyEnrolment(factors));
    ProviderSet providers = new ProviderSet(options.getProviders());
    if (!providers.isEmpty()) {
      verifiers.add(new ProviderVerifier(providers, factors, identifiers));
      enrolments.add(new ProviderEnrolment(providers, factors));
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
