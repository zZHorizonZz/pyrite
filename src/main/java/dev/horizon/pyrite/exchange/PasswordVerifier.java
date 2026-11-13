package dev.horizon.pyrite.exchange;

import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import dev.horizon.pyrite.identity.FactorService;
import dev.horizon.pyrite.identity.Proof;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import java.util.Set;

/**
 * Signing in with the password of the account of the exchange, which proves knowledge. An account without a password
 * hashes a decoy, so that the answer takes as long as one to an account with a password.
 */
final class PasswordVerifier extends FactorVerifier<PasswordAnswer> {

  PasswordVerifier(FactorService factors) {
    super(factors, Tables.Factor.Kind.PASSWORD, PasswordAnswer.getDefaultInstance(),
        Evidence.of(Set.of(Proof.KNOWLEDGE), false));
  }

  @Override
  Future<Set<Proof>> check(Tables.Factor factor, PasswordAnswer answer, byte[] nonce) {
    return factors.verifyPassword(factor, answer.getPassword());
  }

  @Override
  Future<Void> absent() {
    return factors.hashDecoy();
  }
}
