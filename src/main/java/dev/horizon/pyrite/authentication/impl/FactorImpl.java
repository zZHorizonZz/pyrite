package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.impl.resource.BaseResource;
import dev.horizon.pyrite.impl.storage.Row;
import io.vertx.core.Future;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

abstract sealed class FactorImpl extends BaseResource
    permits PasswordFactor, TotpFactor, PasskeyFactor, RecoveryCodesFactor {

  static final String PENDING = "PENDING";
  static final String ACTIVE = "ACTIVE";

  FactorImpl(FactorTypeImpl type, Row row) {
    super(type, row);
  }

  @Override
  public FactorTypeImpl type() {
    return (FactorTypeImpl) super.type();
  }

  public boolean isActive() {
    return ACTIVE.equals(field(FactorType.STATE)) && !isDeleted();
  }

  public Future<Account> account() {
    return parent().map(parent -> (Account) parent.orElseThrow());
  }

  public Future<Factor> activate() {
    return account().compose(Account::factors).compose(factors -> {
      Future<Void> replaced = Future.succeededFuture();
      for (Factor factor : factors) {
        if (factor.getClass() == getClass() && factor.isActive()) {
          replaced = replaced.compose(ignored -> factor.remove());
        }
      }
      return replaced;
    }).compose(ignored -> markActive());
  }

  public Future<Void> remove() {
    Instant now = type().schema().clock().instant();
    return change(Map.of(DELETE_TIME, now, PURGE_TIME, now)).mapEmpty();
  }

  protected Future<Factor> markActive() {
    Map<String, Object> changes = new HashMap<>();
    changes.put(FactorType.STATE, ACTIVE);
    changes.put(PURGE_TIME, null);
    return change(changes).map(Factor.class::cast);
  }

  protected byte[] secret() {
    return field(FactorType.SECRET);
  }

  protected long counter() {
    return field(FactorType.COUNTER);
  }
}
