package dev.horizon.pyrite.flow;

import dev.horizon.pyrite.schema.v1.Stage;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.EnumSet;
import java.util.Set;

/**
 * What the answers of a flow proved so far, which the flow stores as JSON. Knowledge, possession and inherence are the
 * factors, so two answers of one kind count once.
 *
 * @param proofs the proofs
 * @param phishingResistant whether a passkey gave one of them
 */
record Evidence(Set<Proof> proofs, boolean phishingResistant) {

  static final Evidence NOTHING = new Evidence(Set.of(), false);

  enum Proof {
    KNOWLEDGE,
    POSSESSION,
    INHERENCE,
    IDENTIFIER,
    ENROLMENT
  }

  private static final Set<Proof> FACTORS = Set.of(Proof.KNOWLEDGE, Proof.POSSESSION, Proof.INHERENCE);

  Evidence {
    proofs = Set.copyOf(proofs);
  }

  static Evidence of(Proof proof) {
    return new Evidence(Set.of(proof), false);
  }

  static Evidence of(JsonObject json) {
    Set<Proof> proofs = EnumSet.noneOf(Proof.class);
    json.getJsonArray("proofs", new JsonArray()).forEach(proof -> proofs.add(Proof.valueOf((String) proof)));
    return new Evidence(proofs, json.getBoolean("phishing_resistant", false));
  }

  Evidence and(Evidence more) {
    Set<Proof> all = EnumSet.noneOf(Proof.class);
    all.addAll(proofs);
    all.addAll(more.proofs);
    return new Evidence(all, phishingResistant || more.phishingResistant);
  }

  boolean has(Proof proof) {
    return proofs.contains(proof);
  }

  boolean meets(Requirement requirement) {
    Stage stage = requirement.stage();
    if (stage.getEnrol() != Stage.Factor.FACTOR_UNSPECIFIED) {
      return stage.getEnrol() == Stage.Factor.RECOVERY_CODES || has(Proof.ENROLMENT);
    }
    return factors() >= stage.getFactors()
        && (!stage.getPhishingResistant() || phishingResistant)
        && (!stage.getIdentifier() || has(Proof.IDENTIFIER));
  }

  long factors() {
    return proofs.stream().filter(FACTORS::contains).count();
  }

  JsonObject json() {
    JsonArray array = new JsonArray();
    proofs.stream().map(Proof::name).sorted().forEach(array::add);
    return new JsonObject().put("proofs", array).put("phishing_resistant", phishingResistant);
  }
}
