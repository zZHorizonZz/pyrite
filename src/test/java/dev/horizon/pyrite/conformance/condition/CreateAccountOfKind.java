package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.authentication.v1.Account;
import dev.horizon.pyrite.authentication.v1.CreateAccountRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import java.util.Arrays;

/**
 * Creates an account with no identifier and no password, of a kind as it is written, as its own subject.
 */
public record CreateAccountOfKind(String id, String kind) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    Account.Kind named = Arrays.stream(Account.Kind.values()).filter(known -> known.name().equals(kind))
        .findFirst().orElse(Account.Kind.KIND_UNSPECIFIED);
    environment.pyrite().authentication.createAccount(CreateAccountRequest.newBuilder().setSubject("user:" + id)
        .setAccountId(id).setKind(named).build()).await(TIMEOUT);
  }
}
