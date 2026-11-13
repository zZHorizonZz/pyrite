package dev.horizon.pyrite.exchange;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.PasskeyAnswer;
import dev.horizon.pyrite.authentication.v1.PasskeyOptions;
import dev.horizon.pyrite.identity.FactorService;
import dev.horizon.pyrite.identity.Proof;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import java.util.Set;

/**
 * Signing in with a passkey of the account of the exchange, which proves possession, inherence too when the
 * authenticator verified the user, and is phishing-resistant. The challenge holds the request options, whose challenge
 * is the nonce of the stage.
 */
final class PasskeyVerifier extends FactorVerifier<PasskeyAnswer> {

  PasskeyVerifier(FactorService factors) {
    super(factors, Tables.Factor.Kind.PASSKEY, PasskeyAnswer.getDefaultInstance(),
        Evidence.of(Set.of(Proof.POSSESSION, Proof.INHERENCE), true));
  }

  @Override
  public Future<Challenge> challenge(ExchangeRecord exchange) {
    return Future.succeededFuture(Challenge.newBuilder().setMethod(name())
        .setParameters(Any.pack(PasskeyOptions.newBuilder().setOptions(factors.webAuthn().request(exchange.nonce()
            .orElse(new byte[0])).encode()).build()))
        .build());
  }

  @Override
  Future<Set<Proof>> check(Tables.Factor factor, PasskeyAnswer answer, byte[] nonce) {
    return factors.verifyPasskey(factor, answer.getCredential(), nonce);
  }
}
