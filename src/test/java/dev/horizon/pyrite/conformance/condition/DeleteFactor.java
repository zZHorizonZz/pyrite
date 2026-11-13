package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.authentication.v1.DeleteFactorRequest;
import dev.horizon.pyrite.authentication.v1.Factor;
import dev.horizon.pyrite.authentication.v1.ListFactorsRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;

/**
 * Lists the factors of an account as the account, and removes its factor of a kind as a subject.
 */
public record DeleteFactor(String account, String subject, Factor.Kind kind) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    String owner = "user:" + account.substring(account.indexOf('/') + 1);
    Factor factor = environment.pyrite().authentication.listFactors(ListFactorsRequest.newBuilder().setSubject(owner)
        .setAccount(account).build()).await(TIMEOUT).getFactorsList().stream()
        .filter(found -> found.getKind() == kind).findFirst()
        .orElseThrow(() -> new AssertionError("The account " + account + " has no factor " + kind));
    environment.pyrite().authentication.deleteFactor(DeleteFactorRequest.newBuilder().setSubject(subject)
        .setName(factor.getName()).build()).await(TIMEOUT);
  }
}
