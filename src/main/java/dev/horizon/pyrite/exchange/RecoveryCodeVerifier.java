package dev.horizon.pyrite.exchange;

import dev.horizon.pyrite.authentication.v1.RecoveryCodeAnswer;
import dev.horizon.pyrite.identity.FactorService;
import dev.horizon.pyrite.identity.Proof;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import java.util.Set;

/**
 * Signing in with a recovery code of the account of the exchange, which proves possession; each code works once.
 */
final class RecoveryCodeVerifier extends FactorVerifier<RecoveryCodeAnswer> {

  RecoveryCodeVerifier(FactorService factors) {
    super(factors, Tables.Factor.Kind.RECOVERY_CODES, RecoveryCodeAnswer.getDefaultInstance(),
        Evidence.of(Set.of(Proof.POSSESSION), false));
  }

  @Override
  Future<Set<Proof>> check(Tables.Factor factor, RecoveryCodeAnswer answer, byte[] nonce) {
    return factors.verifyRecoveryCode(factor, answer.getCode());
  }
}
