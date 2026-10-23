package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.impl.storage.Row;
import io.vertx.core.Future;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.OptionalLong;

final class TotpFactor extends FactorImpl implements Factor {

  TotpFactor(FactorTypeImpl type, Row row) {
    super(type, row);
  }

  public Future<Boolean> accepts(String code) {
    return totp().compose(totp -> {
      OptionalLong step = totp.match(code, type().schema().clock().instant(), counter());
      return step.isEmpty() ? Future.succeededFuture(false)
          : change(Map.of(FactorType.COUNTER, step.getAsLong())).map(true);
    });
  }

  public Future<String> uri(String label) {
    return totp().map(totp -> totp.uri(type().accounts().options().issuer(), label));
  }

  private Future<TotpSecret> totp() {
    return type().accounts().encryptionKey()
        .compose(key -> key.decrypt(secret(), name().getBytes(StandardCharsets.UTF_8)))
        .map(plain -> new TotpSecret(plain.getPlaintext().toByteArray()));
  }
}
