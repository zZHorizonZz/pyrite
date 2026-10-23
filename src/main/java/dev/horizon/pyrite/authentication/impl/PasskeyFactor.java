package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.impl.storage.Row;
import io.vertx.core.Future;
import java.util.Map;
import java.util.Optional;

final class PasskeyFactor extends FactorImpl implements Factor {

  PasskeyFactor(FactorTypeImpl type, Row row) {
    super(type, row);
  }

  public String credentialId() {
    return field(FactorType.CREDENTIAL_ID);
  }

  public Future<Optional<WebAuthn.Assertion>> accepts(String assertion, byte[] challenge) {
    Optional<WebAuthn.Assertion> asserted = type().webAuthn().verify(assertion, challenge, secret(), credentialId(),
        counter(), field(FactorType.USER_VERIFIED));
    return asserted.isEmpty() ? Future.succeededFuture(asserted)
        : change(Map.of(FactorType.COUNTER, asserted.get().counter())).map(asserted);
  }

  @Override
  public Future<Factor> activate() {
    return markActive();
  }
}
