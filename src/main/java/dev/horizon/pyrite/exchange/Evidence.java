package dev.horizon.pyrite.exchange;

import dev.horizon.pyrite.identity.Proof;
import dev.horizon.pyrite.schema.v1.Stage;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.EnumSet;
import java.util.Set;

/**
 * What the answers of an exchange proved so far, which the exchange stores as JSON: the factors, whether a code proved
 * the identifier, whether a credential was enrolled, and whether a phishing-resistant answer gave a factor. Two answers
 * that prove the same factor count once.
 *
 * @param factors the factors that the answers proved
 * @param identifier whether a code proved that the caller receives messages at the identifier
 * @param enrolment whether an answer enrolled a credential
 * @param phishingResistant whether a passkey gave one of the factors, or was enrolled
 */
record Evidence(Set<Proof> factors, boolean identifier, boolean enrolment, boolean phishingResistant) {

  static final Evidence NOTHING = new Evidence(Set.of(), false, false, false);
  static final Evidence IDENTIFIER = new Evidence(Set.of(), true, false, false);
  static final Evidence ENROLMENT = new Evidence(Set.of(), false, true, false);

  Evidence {
    factors = Set.copyOf(factors);
  }

  static Evidence of(Set<Proof> factors, boolean phishingResistant) {
    return new Evidence(factors, false, false, phishingResistant);
  }

  static Evidence of(JsonObject json) {
    Set<Proof> factors = EnumSet.noneOf(Proof.class);
    json.getJsonArray("factors", new JsonArray()).forEach(factor -> factors.add(Proof.valueOf((String) factor)));
    return new Evidence(factors, json.getBoolean("identifier", false), json.getBoolean("enrolment", false),
        json.getBoolean("phishing_resistant", false));
  }

  Evidence and(Evidence more) {
    Set<Proof> all = EnumSet.noneOf(Proof.class);
    all.addAll(factors);
    all.addAll(more.factors);
    return new Evidence(all, identifier || more.identifier, enrolment || more.enrolment,
        phishingResistant || more.phishingResistant);
  }

  /**
   * Tells whether the evidence meets a stage: an enrolment once the credential is enrolled, and any other stage once
   * it holds the factors, the phishing resistance and the proof of the identifier that the stage asks for.
   */
  boolean meets(Stage stage) {
    if (stage.getEnrol() != Stage.Factor.FACTOR_UNSPECIFIED) {
      return stage.getEnrol() == Stage.Factor.RECOVERY_CODES || enrolment;
    }
    return factors.size() >= stage.getFactors()
        && (!stage.getPhishingResistant() || phishingResistant)
        && (!stage.getIdentifier() || identifier);
  }

  JsonObject json() {
    JsonArray array = new JsonArray();
    factors.stream().map(Proof::name).sorted().forEach(array::add);
    return new JsonObject().put("factors", array).put("identifier", identifier).put("enrolment", enrolment)
        .put("phishing_resistant", phishingResistant);
  }
}
