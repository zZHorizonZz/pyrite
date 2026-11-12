package dev.horizon.pyrite.flow;

import dev.horizon.pyrite.schema.v1.Stage;
import java.util.ArrayList;
import java.util.List;

/**
 * A stage of a flow definition, as the answers that it takes.
 *
 * @param stage the stage
 */
record Requirement(Stage stage) {

  /**
   * Gives the answers that the stage takes, after the evidence so far: only those that add what it lacks. A
   * phishing-resistant stage takes a passkey before anything else, and an enrolment takes the new factor only.
   *
   * @param evidence what the answers proved so far
   * @return the answers, in the order of the challenges
   */
  List<Authentication<?>> authentications(Evidence evidence) {
    if (stage.getEnrol() != Stage.Factor.FACTOR_UNSPECIFIED) {
      return switch (stage.getEnrol()) {
        case PASSWORD -> List.of(new PasswordEnrolment());
        case TOTP -> List.of(new TotpEnrolment());
        case PASSKEY -> List.of(new PasskeyEnrolment());
        case RECOVERY_CODES, FACTOR_UNSPECIFIED, UNRECOGNIZED -> List.of();
      };
    }
    List<Authentication<?>> authentications = new ArrayList<>();
    if (stage.getIdentifier() && !evidence.has(Evidence.Proof.IDENTIFIER)) {
      authentications.add(new CodeAuthentication());
    }
    if (stage.getPhishingResistant() && !evidence.phishingResistant()) {
      authentications.add(new PasskeyAuthentication());
    } else if (evidence.factors() < stage.getFactors()) {
      if (!evidence.has(Evidence.Proof.KNOWLEDGE)) {
        authentications.add(new PasswordAuthentication());
      }
      if (!evidence.has(Evidence.Proof.POSSESSION)) {
        authentications.add(new TotpAuthentication());
        authentications.add(new RecoveryCodeAuthentication());
      }
      if (!evidence.has(Evidence.Proof.POSSESSION) || !evidence.has(Evidence.Proof.INHERENCE)) {
        authentications.add(new PasskeyAuthentication());
      }
    }
    return authentications;
  }
}
