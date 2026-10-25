package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import java.util.Optional;

final class PasskeyFactor extends FactorImpl implements Factor {

  PasskeyFactor(AccountTypeImpl type, Tables.Factor row) {
    super(type, row);
  }

  public String credentialId() {
    return row().getCredentialId();
  }

  public Future<Optional<WebAuthn.Assertion>> accepts(String assertion, byte[] challenge) {
    Optional<WebAuthn.Assertion> asserted = type().webAuthn().verify(assertion, challenge, secret(), credentialId(),
        counter(), row().getUserVerified());
    return asserted.isEmpty() ? Future.succeededFuture(asserted)
        : change(row().toBuilder().setCounter(asserted.get().counter())).map(asserted);
  }

  @Override
  public Future<Factor> activate() {
    return markActive();
  }
}
