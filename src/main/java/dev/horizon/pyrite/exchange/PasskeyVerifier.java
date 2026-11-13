package dev.horizon.pyrite.exchange;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.PasskeyAnswer;
import dev.horizon.pyrite.authentication.v1.PasskeyOptions;
import dev.horizon.pyrite.identity.PasskeyCredential;
import dev.horizon.pyrite.identity.Proof;
import dev.horizon.pyrite.identity.WebAuthn;
import io.vertx.core.Future;
import java.util.Set;

/**
 * Signing in with a passkey of the account of the exchange, which proves possession, inherence too when the
 * authenticator verified the user, and is phishing-resistant. The challenge holds the request options, whose challenge
 * is the nonce of the stage.
 */
final class PasskeyVerifier extends CredentialVerifier<PasskeyCredential, PasskeyAnswer> {

  private final WebAuthn webAuthn;

  PasskeyVerifier(WebAuthn webAuthn) {
    super(PasskeyCredential.class, PasskeyAnswer.getDefaultInstance(),
        Evidence.of(Set.of(Proof.POSSESSION, Proof.INHERENCE), true), Future::succeededFuture);
    this.webAuthn = webAuthn;
  }

  @Override
  public Future<Challenge> challenge(Exchange exchange) {
    return Future.succeededFuture(Challenge.newBuilder().setMethod(name())
        .setParameters(Any.pack(PasskeyOptions.newBuilder().setOptions(webAuthn.request(exchange.nonce()
            .orElse(new byte[0])).encode()).build()))
        .build());
  }
}
