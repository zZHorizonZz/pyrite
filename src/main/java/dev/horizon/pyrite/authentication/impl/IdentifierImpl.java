package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.impl.model.DeclaredKind;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;

final class IdentifierImpl implements Identifier {

  private final AccountTypeImpl type;
  private final Tables.Identifier row;

  IdentifierImpl(AccountTypeImpl type, Tables.Identifier row) {
    this.type = type;
    this.row = row;
  }

  @Override
  public String name() {
    return row.getName();
  }

  @Override
  public String account() {
    return row.getParent();
  }

  @Override
  public String kind() {
    return row.getKind();
  }

  @Override
  public String value() {
    return row.getValue();
  }

  @Override
  public boolean isVerified() {
    return row.getVerified();
  }

  @Override
  public boolean isDeliverable() {
    return !row.hasDeleteTime() && type.schema().types().identifierKind(kind()).map(DeclaredKind::receivesCodes)
        .orElse(false);
  }

  @Override
  public Future<Identifier> verify() {
    return type.identifierRows().update(row.toBuilder().setVerified(true).setRevision(row.getRevision() + 1)
        .setUpdateTime(type.schema().clock().instant().toString()).build(), row.getUid(), row.getRevision(), name())
        .map(changed -> new IdentifierImpl(type, changed));
  }

  @Override
  public dev.horizon.pyrite.authentication.v1.Identifier message() {
    return dev.horizon.pyrite.authentication.v1.Identifier.newBuilder().setName(name()).setAccount(account())
        .setKind(kind()).setValue(value()).setVerified(isVerified()).setPrimary(row.getPrimary()).build();
  }
}
