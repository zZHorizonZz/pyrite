package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;

abstract sealed class FactorImpl implements Factor
    permits PasswordFactor, TotpFactor, PasskeyFactor, RecoveryCodesFactor {

  static final String PENDING = "PENDING";
  static final String ACTIVE = "ACTIVE";

  private final AccountTypeImpl type;
  private final Tables.Factor row;

  FactorImpl(AccountTypeImpl type, Tables.Factor row) {
    this.type = type;
    this.row = row;
  }

  @Override
  public String name() {
    return row.getName();
  }

  @Override
  public boolean isActive() {
    return ACTIVE.equals(row.getState()) && !row.hasDeleteTime();
  }

  @Override
  public Future<Account> account() {
    return type.account(row.getParent());
  }

  @Override
  public Future<Factor> activate() {
    return type.factors(row.getParent()).compose(factors -> {
      Future<Void> replaced = Future.succeededFuture();
      for (Factor factor : factors) {
        if (factor.getClass() == getClass() && factor.isActive()) {
          replaced = replaced.compose(ignored -> factor.remove());
        }
      }
      return replaced;
    }).compose(ignored -> markActive());
  }

  @Override
  public Future<Void> remove() {
    String now = type.schema().clock().instant().toString();
    return change(row.toBuilder().setDeleteTime(now).setPurgeTime(now)).mapEmpty();
  }

  @Override
  public dev.horizon.pyrite.authentication.v1.Factor message() {
    return dev.horizon.pyrite.authentication.v1.Factor.newBuilder().setName(name()).setAccount(row.getParent())
        .setKind(dev.horizon.pyrite.authentication.v1.Factor.Kind.valueOf(row.getKind())).build();
  }

  protected Future<Factor> markActive() {
    return change(row.toBuilder().setState(ACTIVE).clearPurgeTime());
  }

  protected AccountTypeImpl type() {
    return type;
  }

  protected Tables.Factor row() {
    return row;
  }

  protected byte[] secret() {
    return row.getSecret().toByteArray();
  }

  protected long counter() {
    return row.getCounter();
  }

  protected Future<Factor> change(Tables.Factor.Builder changed) {
    return type.factorRows().update(changed.setRevision(row.getRevision() + 1)
        .setUpdateTime(type.schema().clock().instant().toString()).build(), row.getUid(), row.getRevision(), name())
        .map(type::factor);
  }
}
