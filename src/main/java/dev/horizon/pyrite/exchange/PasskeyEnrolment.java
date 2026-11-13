package dev.horizon.pyrite.exchange;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.PasskeyAnswer;
import dev.horizon.pyrite.authentication.v1.PasskeyOptions;
import dev.horizon.pyrite.identity.CredentialType;
import dev.horizon.pyrite.identity.PasskeyCredential;
import dev.horizon.pyrite.identity.WebAuthn;
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

  private final CredentialType credentials;

  PasskeyEnrolment(CredentialType credentials) {
    this.credentials = credentials;
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
  public Future<Challenge> challenge(Exchange exchange) {
    return exchange.claimant().credentials(PasskeyCredential.class).map(passkeys -> Challenge.newBuilder()
        .setMethod(name()).setParameters(Any.pack(PasskeyOptions.newBuilder().setOptions(credentials.webAuthn()
            .creation(exchange.claimant().accountName(), exchange.nonce().orElse(new byte[0]),
                passkeys.stream().map(PasskeyCredential::credentialId).toList())
            .encode()).build()))
        .build());
  }

  @Override
  public Future<Optional<Evidence>> verify(Exchange exchange, PasskeyAnswer answer) {
    Optional<WebAuthn.Registration> registration = exchange.nonce().flatMap(nonce -> credentials.webAuthn()
        .register(answer.getCredential(), nonce));
    if (registration.isEmpty()) {
      return Future.succeededFuture(Optional.empty());
    }
    return exchange.claimant().requireAccount()
        .compose(account -> credentials.enrolPasskey(account.name(), registration.get())).map(passkey -> {
          exchange.enrol(passkey);
          return Optional.of(PROVES);
        });
  }
}
