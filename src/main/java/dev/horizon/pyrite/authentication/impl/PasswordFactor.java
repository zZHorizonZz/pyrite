package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.impl.storage.Row;
import io.vertx.core.Future;
import java.nio.charset.StandardCharsets;

final class PasswordFactor extends FactorImpl implements Factor {

  PasswordFactor(FactorTypeImpl type, Row row) {
    super(type, row);
  }

  public Future<Boolean> matches(String password) {
    PasswordHash hash = new PasswordHash(new String(secret(), StandardCharsets.UTF_8));
    return type().schema().vertx().executeBlocking(() -> hash.matches(password));
  }
}
