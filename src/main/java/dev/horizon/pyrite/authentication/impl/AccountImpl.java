package dev.horizon.pyrite.authentication.impl;

import com.google.protobuf.Timestamp;
import dev.horizon.pyrite.impl.model.TypeSystem;
import dev.horizon.pyrite.impl.resource.Resource;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.schema.v1.Stage;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

final class AccountImpl implements Account {

  private static final int RECOVERY_CODES = 10;
  private static final int RECOVERY_CODE_BYTES = 10;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final AccountTypeImpl type;
  private final Tables.Account row;

  AccountImpl(AccountTypeImpl type, Tables.Account row) {
    this.type = type;
    this.row = row;
  }

  @Override
  public String name() {
    return row.getName();
  }

  @Override
  public String subject() {
    return TypeSystem.USER + ":" + name().substring(name().indexOf('/') + 1);
  }

  @Override
  public Resource resource() {
    return type.resource(name());
  }

  @Override
  public Future<List<Identifier>> identifiers() {
    return type.identifiers(name());
  }

  @Override
  public Future<List<Factor>> factors() {
    return type.factors(name());
  }

  @Override
  public Future<Session> createSession() {
    return type.issue(this);
  }

  @Override
  public Future<Void> deleteSessions() {
    return type.sessions(name()).compose(found -> {
      Future<Void> ended = Future.succeededFuture();
      for (Session session : found) {
        if (session.isLive()) {
          ended = ended.compose(ignored -> session.end());
        }
      }
      return ended;
    });
  }

  @Override
  public dev.horizon.pyrite.authentication.v1.Account message() {
    Instant created = Instant.parse(row.getCreateTime());
    return dev.horizon.pyrite.authentication.v1.Account.newBuilder().setName(name())
        .setKind(dev.horizon.pyrite.authentication.v1.Account.Kind.valueOf(row.getKind()))
        .setCreateTime(Timestamp.newBuilder().setSeconds(created.getEpochSecond()).setNanos(created.getNano()))
        .build();
  }

  Future<PasswordFactor> enrolPassword(PasswordHash hash) {
    return type.enrol(this, UUID.randomUUID().toString(), Stage.Factor.PASSWORD,
        hash.encoded().getBytes(StandardCharsets.UTF_8), "", 0, false).map(PasswordFactor.class::cast);
  }

  Future<TotpFactor> enrolTotp() {
    String id = UUID.randomUUID().toString();
    return type.encryptionKey()
        .compose(key -> key.encrypt(TotpSecret.generate().bytes(), (AccountTypeImpl.FACTORS + id)
            .getBytes(StandardCharsets.UTF_8)))
        .compose(sealed -> type.enrol(this, id, Stage.Factor.TOTP,
            sealed.getCiphertext().toByteArray(), "", -1, false))
        .map(TotpFactor.class::cast);
  }

  Future<PasskeyFactor> enrolPasskey(WebAuthn.Registration registration) {
    return type.enrol(this, UUID.randomUUID().toString(), Stage.Factor.PASSKEY,
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
    return type.macKey().compose(key -> {
      Future<Void> signed = Future.succeededFuture();
      for (String code : codes) {
        signed = signed.compose(ignored -> key.sign(RecoveryCodesFactor.data(code)).map(mac -> {
          macs.add(Base64.getEncoder().encodeToString(mac.getSignature().toByteArray()));
          return null;
        }));
      }
      return signed;
    }).compose(ignored -> type.enrol(this, UUID.randomUUID().toString(),
        Stage.Factor.RECOVERY_CODES, macs.encode().getBytes(StandardCharsets.UTF_8), "", 0, false))
        .map(factor -> ((RecoveryCodesFactor) factor).withCodes(codes));
  }
}
