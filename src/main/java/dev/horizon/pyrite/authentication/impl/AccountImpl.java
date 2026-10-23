package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.impl.model.TypeSystem;
import dev.horizon.pyrite.impl.resource.BaseResource;
import dev.horizon.pyrite.impl.storage.Row;
import dev.horizon.pyrite.schema.v1.Stage;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

final class AccountImpl extends BaseResource implements Account {

  private static final int RECOVERY_CODES = 10;
  private static final int RECOVERY_CODE_BYTES = 10;
  private static final SecureRandom RANDOM = new SecureRandom();

  AccountImpl(AccountTypeImpl type, Row row) {
    super(type, row);
  }

  @Override
  public AccountTypeImpl type() {
    return (AccountTypeImpl) super.type();
  }

  @Override
  public String subject() {
    return TypeSystem.USER + ":" + id();
  }

  @Override
  public Future<List<Identifier>> identifiers() {
    return type().identifiers().under(name());
  }

  @Override
  public Future<List<Factor>> factors() {
    return type().factors().under(name());
  }

  @Override
  public Future<Session> createSession() {
    return type().sessions().issue(this);
  }

  @Override
  public Future<List<Session>> sessions() {
    return type().sessions().under(name());
  }

  @Override
  public Future<Void> deleteSessions() {
    return sessions().compose(found -> {
      Future<Void> ended = Future.succeededFuture();
      for (Session session : found) {
        if (!session.isDeleted()) {
          ended = ended.compose(ignored -> session.end());
        }
      }
      return ended;
    });
  }

  Future<PasswordFactor> enrolPassword(PasswordHash hash) {
    return type().factors().enrol(this, UUID.randomUUID().toString(), Stage.Factor.PASSWORD,
        hash.encoded().getBytes(StandardCharsets.UTF_8), "", 0, false).map(PasswordFactor.class::cast);
  }

  Future<TotpFactor> enrolTotp() {
    String id = UUID.randomUUID().toString();
    return type().encryptionKey()
        .compose(key -> key.encrypt(TotpSecret.generate().bytes(), type().factors().declaration().resource(id)
            .getBytes(StandardCharsets.UTF_8)))
        .compose(sealed -> type().factors().enrol(this, id, Stage.Factor.TOTP,
            sealed.getCiphertext().toByteArray(), "", -1, false))
        .map(TotpFactor.class::cast);
  }

  Future<PasskeyFactor> enrolPasskey(WebAuthn.Registration registration) {
    return type().factors().enrol(this, UUID.randomUUID().toString(), Stage.Factor.PASSKEY,
        registration.credential(), registration.credentialId(), registration.counter(), registration.userVerified())
        .map(PasskeyFactor.class::cast);
  }

  Future<RecoveryCodesFactor> createRecoveryCodes() {
    List<String> codes = new ArrayList<>();
    for (int index = 0; index < RECOVERY_CODES; index++) {
      byte[] code = new byte[RECOVERY_CODE_BYTES];
      RANDOM.nextBytes(code);
      codes.add(Base64.getUrlEncoder().withoutPadding().encodeToString(code));
    }
    JsonArray macs = new JsonArray();
    return type().macKey().compose(key -> {
      Future<Void> signed = Future.succeededFuture();
      for (String code : codes) {
        signed = signed.compose(ignored -> key.sign(RecoveryCodesFactor.data(code)).map(mac -> {
          macs.add(Base64.getEncoder().encodeToString(mac.getSignature().toByteArray()));
          return null;
        }));
      }
      return signed;
    }).compose(ignored -> type().factors().enrol(this, UUID.randomUUID().toString(),
        Stage.Factor.RECOVERY_CODES, macs.encode().getBytes(StandardCharsets.UTF_8), "", 0, false))
        .map(factor -> ((RecoveryCodesFactor) factor).withCodes(codes));
  }
}
