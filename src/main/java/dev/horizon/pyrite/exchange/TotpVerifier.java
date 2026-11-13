package dev.horizon.pyrite.exchange;

import dev.horizon.pyrite.authentication.v1.TotpAnswer;
import dev.horizon.pyrite.identity.FactorService;
import dev.horizon.pyrite.identity.Proof;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import java.util.Set;

/**
 * Signing in with a code of the TOTP authenticator of the account of the exchange, which proves possession.
 */
final class TotpVerifier extends FactorVerifier<TotpAnswer> {

  TotpVerifier(FactorService factors) {
    super(factors, Tables.Factor.Kind.TOTP, TotpAnswer.getDefaultInstance(),
        Evidence.of(Set.of(Proof.POSSESSION), false));
  }

  @Override
  Future<Set<Proof>> check(Tables.Factor factor, TotpAnswer answer, byte[] nonce) {
    return factors.verifyTotp(factor, answer.getCode());
  }
}
