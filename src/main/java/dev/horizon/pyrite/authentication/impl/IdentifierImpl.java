package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.authentication.v1.IdentifierKind;
import dev.horizon.pyrite.impl.resource.BaseResource;
import dev.horizon.pyrite.impl.storage.Row;
import io.vertx.core.Future;
import java.util.Map;

final class IdentifierImpl extends BaseResource implements Identifier {

  IdentifierImpl(IdentifierTypeImpl type, Row row) {
    super(type, row);
  }

  @Override
  public IdentifierKind kind() {
    return IdentifierKind.valueOf((String) field(IdentifierType.KIND));
  }

  @Override
  public String value() {
    return field(IdentifierType.VALUE);
  }

  @Override
  public boolean isVerified() {
    return field(IdentifierType.VERIFIED);
  }

  @Override
  public boolean isDeliverable() {
    return kind() != IdentifierKind.USERNAME && !isDeleted();
  }

  @Override
  public Delivery.Channel channel() {
    return kind() == IdentifierKind.PHONE ? Delivery.Channel.SMS : Delivery.Channel.EMAIL;
  }

  @Override
  public String mask() {
    String value = value();
    return kind() == IdentifierKind.EMAIL ? value.charAt(0) + "***" + value.substring(value.indexOf('@'))
        : "***" + value.substring(Math.max(0, value.length() - 2));
  }

  @Override
  public Future<Identifier> verify() {
    return change(Map.of(IdentifierType.VERIFIED, true)).map(Identifier.class::cast);
  }
}
