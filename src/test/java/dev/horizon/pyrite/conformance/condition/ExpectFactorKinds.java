package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.authentication.v1.Factor;
import dev.horizon.pyrite.authentication.v1.ListFactorsRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import java.util.List;

/**
 * Lists the factors of an account, as a subject, and checks that they are of exactly these kinds, in any order.
 */
public record ExpectFactorKinds(String subject, String account, List<Factor.Kind> kinds) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    List<Factor> factors = environment.pyrite().authentication.listFactors(ListFactorsRequest.newBuilder()
        .setSubject(subject).setAccount(account).build()).await(TIMEOUT).getFactorsList();
    assertThat(factors).extracting(Factor::getKind).containsExactlyInAnyOrderElementsOf(kinds);
  }
}
