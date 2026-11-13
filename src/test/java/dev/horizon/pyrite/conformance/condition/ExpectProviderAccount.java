package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.authentication.v1.Exchange;
import dev.horizon.pyrite.authentication.v1.Factor;
import dev.horizon.pyrite.authentication.v1.TokenSet;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the last exchange signed in an account, and issued its tokens. With no account named, it checks that the
 * exchange created a new account with a linked provider account, and leaves it; otherwise, that it is the account
 * named, or the one left.
 */
public record ExpectProviderAccount(String account) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    Exchange exchange = environment.get(Value.EXCHANGE);
    String reached = exchange.getResult().getAccount();
    if (account.isEmpty()) {
      assertThat(reached).as("account").startsWith("accounts/u");
      assertThat(exchange.getResult().getFactorsList()).as("factors").singleElement()
          .satisfies(factor -> assertThat(factor.getKind()).isEqualTo(Factor.Kind.PROVIDER));
      environment.put(Value.PROVIDER_ACCOUNT, reached);
    } else {
      assertThat(reached).as("account").isEqualTo(account.equals("*") ? environment.get(Value.PROVIDER_ACCOUNT)
          : account);
    }
    assertThat(exchange.getResult().getIssued(0).unpack(TokenSet.class).getAccessToken()).as("access token")
        .isNotEmpty();
  }
}
