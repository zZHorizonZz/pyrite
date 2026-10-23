package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.schema.v1.Stage;
import java.util.ArrayList;
import java.util.List;

record Requirement(Stage stage) {

  List<Method<?>> methods(Evidence evidence) {
    if (stage.getEnrol() != Stage.Factor.FACTOR_UNSPECIFIED) {
      return switch (stage.getEnrol()) {
        case PASSWORD -> List.of(new PasswordEnrolment());
        case TOTP -> List.of(new TotpEnrolment());
        case PASSKEY -> List.of(new PasskeyEnrolment());
        case RECOVERY_CODES, FACTOR_UNSPECIFIED, UNRECOGNIZED -> List.of();
      };
    }
    List<Method<?>> methods = new ArrayList<>();
    if (stage.getIdentifier() && !evidence.has(Evidence.Proof.IDENTIFIER)) {
      methods.add(new CodeMethod());
    }
    if (stage.getPhishingResistant() && !evidence.phishingResistant()) {
      methods.add(new PasskeyMethod());
    } else if (evidence.factors() < stage.getFactors()) {
      if (!evidence.has(Evidence.Proof.KNOWLEDGE)) {
        methods.add(new PasswordMethod());
      }
      if (!evidence.has(Evidence.Proof.POSSESSION)) {
        methods.add(new TotpMethod());
        methods.add(new RecoveryCodeMethod());
      }
      if (!evidence.has(Evidence.Proof.POSSESSION) || !evidence.has(Evidence.Proof.INHERENCE)) {
        methods.add(new PasskeyMethod());
      }
    }
    return methods;
  }
}
