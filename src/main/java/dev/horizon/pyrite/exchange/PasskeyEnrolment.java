package dev.horizon.pyrite.exchange;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.PasskeyAnswer;
import dev.horizon.pyrite.authentication.v1.PasskeyOptions;
import dev.horizon.pyrite.identity.FactorService;
import dev.horizon.pyrite.identity.WebAuthn;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.schema.v1.Stage;
import io.vertx.core.Future;
import java.util.Optional;
import java.util.Set;

/**
 * The enrolment of a new passkey: the creation options hold the nonce of the stage as their challenge and exclude the
 * passkeys of the account, and a right answer enrols the passkey.
 */
final class PasskeyEnrolment implements Enrolling<PasskeyAnswer> {

  private static final Evidence PROVES = new Evidence(Set.of(), false, true, true);

  private final FactorService factors;

  PasskeyEnrolment(FactorService factors) {
    this.factors = factors;
  }

  @Override
  public PasskeyAnswer prototype() {
    return PasskeyAnswer.getDefaultInstance();
  }

  @Override
  public Evidence proves() {
    return PROVES;
  }

  @Override
  public Stage.Factor enrols() {
    return Stage.Factor.PASSKEY;
  }

  @Override
  public Future<Challenge> challenge(ExchangeRecord exchange) {
    return exchange.claimant().factors(Tables.Factor.Kind.PASSKEY).map(passkeys -> Challenge.newBuilder()
        .setMethod(name()).setParameters(Any.pack(PasskeyOptions.newBuilder().setOptions(factors.webAuthn()
            .creation(exchange.claimant().accountName(), exchange.nonce().orElse(new byte[0]),
                passkeys.stream().map(Tables.Factor::getCredentialId).toList())
            .encode()).build()))
        .build());
  }

  @Override
  public Future<Optional<Evidence>> verify(ExchangeRecord exchange, PasskeyAnswer answer) {
    Optional<WebAuthn.Registration> registration = exchange.nonce().flatMap(nonce -> factors.webAuthn()
        .register(answer.getCredential(), nonce));
    if (registration.isEmpty()) {
      return Future.succeededFuture(Optional.empty());
    }
    return exchange.claimant().requireAccount()
        .compose(account -> factors.enrolPasskey(account.getName(), registration.get())).map(passkey -> {
          exchange.enrol(passkey);
          return Optional.of(PROVES);
        });
  }
}
