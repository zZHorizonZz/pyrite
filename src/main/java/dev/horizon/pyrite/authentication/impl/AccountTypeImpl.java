package dev.horizon.pyrite.authentication.impl;

import static dev.horizon.basepoint.Filter.and;
import static dev.horizon.basepoint.Filter.eq;
import static dev.horizon.basepoint.Filter.isNull;
import static dev.horizon.basepoint.Filter.lte;
import static dev.horizon.basepoint.Filter.or;

import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.PyriteServiceOptions;
import dev.horizon.pyrite.authentication.v1.Principal;
import dev.horizon.pyrite.crypto.impl.EncryptionKey;
import dev.horizon.pyrite.crypto.impl.SigningKey;
import dev.horizon.pyrite.impl.model.DeclaredKind;
import dev.horizon.pyrite.impl.model.DeclaredType;
import dev.horizon.pyrite.impl.model.TypeSystem;
import dev.horizon.pyrite.impl.resource.Resource;
import dev.horizon.pyrite.impl.resource.Schema;
import dev.horizon.pyrite.impl.resource.Subject;
import dev.horizon.pyrite.impl.storage.AccountBackend;
import dev.horizon.pyrite.impl.storage.FactorBackend;
import dev.horizon.pyrite.impl.storage.IdentifierBackend;
import dev.horizon.pyrite.impl.storage.SessionBackend;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.schema.v1.Stage;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AccountTypeImpl {

  static final String IDENTIFIERS = "identifiers/";
  static final String FACTORS = "factors/";
  static final String SESSIONS = "sessions/";

  private static final List<String> KINDS = List.of("HUMAN", "SERVICE");

  private final Schema schema;
  private final DeclaredType declared;
  private final AccountBackend accounts;
  private final IdentifierBackend identifiers;
  private final FactorBackend factors;
  private final SessionBackend sessions;
  private final WebAuthn webAuthn;

  public AccountTypeImpl(Schema schema, DeclaredType declared, Database database) {
    this.schema = schema;
    this.declared = declared;
    this.accounts = new AccountBackend(database);
    this.identifiers = new IdentifierBackend(database);
    this.factors = new FactorBackend(database);
    this.sessions = new SessionBackend(database);
    this.webAuthn = new WebAuthn(schema.options().authentication().orElseThrow().relyingParty());
  }

  public Future<Account> create(Subject creator, String id, String kind) {
    if (!KINDS.contains(kind)) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "An account's kind is one of " + KINDS + ".");
    }
    return schema.create(creator, declared.resource(id), "", Struct.getDefaultInstance()).compose(resource -> {
      String now = schema.clock().instant().toString();
      Tables.Account row = Tables.Account.newBuilder().setUid(UUID.randomUUID().toString()).setName(resource.name())
          .setLabels("{}").setRevision(1).setCreateTime(now).setUpdateTime(now).setKind(kind).build();
      return accounts.insert(row)
          .compose(ignored -> schema.adapter().write(TupleKey.newBuilder().setUser(resource.object())
              .setRelation(TypeSystem.OWNER).setObject(resource.object()).build()))
          .map(ignored -> (Account) new AccountImpl(this, row));
    });
  }

  public Future<Account> account(String name) {
    return accounts.findByName(name).map(found -> new AccountImpl(this, found.orElseThrow(() -> new PyriteException(
        GrpcStatus.NOT_FOUND, "The account " + name + " does not exist."))));
  }

  public Future<Account> account(Subject subject) {
    if (!subject.type().equals(TypeSystem.USER)) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The subject " + subject + " is not an account.");
    }
    return account(declared.resource(subject.id()));
  }

  public Future<Void> delete(Account account) {
    return identifiers.deleteChildren(account.name())
        .compose(ignored -> factors.deleteChildren(account.name()))
        .compose(ignored -> sessions.deleteChildren(account.name()))
        .compose(ignored -> accounts.delete().where(eq("name", account.name())).execute())
        .compose(ignored -> schema.forget(account.name()));
  }

  public Future<Identifier> addIdentifier(Account account, String kind, String value, boolean primary) {
    DeclaredKind declared = kind(kind, value).orElseThrow(() -> new PyriteException(GrpcStatus.INVALID_ARGUMENT,
        kind.isEmpty() ? "The value " + value + " is of no identifier kind of the declaration."
            : "The declaration has no identifier kind " + kind + "."));
    String normalized = declared.normalized(value).orElseThrow(() -> new PyriteException(
        GrpcStatus.INVALID_ARGUMENT, "The value " + value + " is not of the identifier kind " + declared.name()
            + "."));
    return identifier(declared.name(), normalized).compose(found -> {
      if (found.isPresent()) {
        throw new PyriteException(GrpcStatus.ALREADY_EXISTS, "The " + declared.name() + " " + normalized
            + " is another account's or this one's already.");
      }
      String now = schema.clock().instant().toString();
      Tables.Identifier row = Tables.Identifier.newBuilder().setUid(UUID.randomUUID().toString())
          .setName(IDENTIFIERS + UUID.randomUUID()).setParent(account.name()).setLabels("{}").setRevision(1)
          .setCreateTime(now).setUpdateTime(now).setKind(declared.name()).setValue(normalized).setVerified(false)
          .setPrimary(primary).build();
      return identifiers.insert(row).map(ignored -> (Identifier) new IdentifierImpl(this, row));
    });
  }

  public Future<List<Identifier>> identifiers(String account) {
    return identifiers.children(account).map(rows -> rows.stream()
        .<Identifier> map(row -> new IdentifierImpl(this, row)).toList());
  }

  public Future<Optional<Identifier>> find(String kind, String value) {
    Optional<DeclaredKind> declared = kind(kind, value);
    if (!kind.isEmpty() && declared.isEmpty()) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The declaration has no identifier kind " + kind + ".");
    }
    Optional<String> normalized = declared.flatMap(found -> found.normalized(value));
    if (!kind.isEmpty() && normalized.isEmpty()) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The value " + value + " is not of the identifier kind "
          + kind + ".");
    }
    return normalized.isEmpty() ? Future.succeededFuture(Optional.empty())
        : identifier(declared.get().name(), normalized.get());
  }

  public Future<Session> session(String name) {
    return sessions.findByName(name).map(found -> new SessionImpl(this, found.orElseThrow(() -> new PyriteException(
        GrpcStatus.NOT_FOUND, "The session " + name + " does not exist.")), Optional.empty()));
  }

  public Future<Principal> authenticate(String token) {
    SessionToken parsed = SessionToken.parse(token);
    return sessions.findByName(SESSIONS + parsed.id()).compose(found -> found
        .map(row -> new SessionImpl(this, row, Optional.empty()).authenticate(parsed.secret()))
        .orElseGet(() -> Future.failedFuture(new PyriteException(GrpcStatus.UNAUTHENTICATED, "The session has"
            + " ended."))));
  }

  public Future<Long> purge() {
    String now = schema.clock().instant().toString();
    return factors.delete().where(lte("purge_time", now)).execute()
        .compose(removed -> sessions.delete().where(or(lte("purge_time", now), lte("expire_time", now))).execute()
            .map(ended -> removed.longValue() + ended.longValue()));
  }

  Schema schema() {
    return schema;
  }

  Resource resource(String name) {
    return new Resource(schema, declared, name);
  }

  PyriteServiceOptions.Authentication options() {
    return schema.options().authentication().orElseThrow();
  }

  WebAuthn webAuthn() {
    return webAuthn;
  }

  Future<SigningKey> macKey() {
    return schema.keys().signing(options().macKey());
  }

  Future<EncryptionKey> encryptionKey() {
    return schema.keys().encryption(options().encryptionKey());
  }

  AccountBackend accounts() {
    return accounts;
  }

  IdentifierBackend identifierRows() {
    return identifiers;
  }

  FactorBackend factorRows() {
    return factors;
  }

  SessionBackend sessionRows() {
    return sessions;
  }

  Future<Optional<Identifier>> identifierNamed(String name) {
    return identifiers.findByName(name).map(found -> found.map(row -> new IdentifierImpl(this, row)));
  }

  Future<List<Factor>> factors(String account) {
    return factors.children(account).map(rows -> rows.stream().<Factor> map(this::factor).toList());
  }

  Future<Factor> factor(String name) {
    return factors.findByName(name).map(found -> factor(found.orElseThrow(() -> new PyriteException(GrpcStatus.NOT_FOUND,
        "The factor " + name + " does not exist."))));
  }

  Factor factor(Tables.Factor row) {
    return switch (Stage.Factor.valueOf(row.getKind())) {
      case PASSWORD -> new PasswordFactor(this, row);
      case TOTP -> new TotpFactor(this, row);
      case PASSKEY -> new PasskeyFactor(this, row);
      case RECOVERY_CODES -> new RecoveryCodesFactor(this, row);
      case FACTOR_UNSPECIFIED, UNRECOGNIZED -> throw new IllegalStateException("The factor " + row.getName()
          + " has no kind.");
    };
  }

  Future<Factor> enrol(Account account, String id, Stage.Factor kind, byte[] secret, String credentialId, long counter,
      boolean userVerified) {
    Instant now = schema.clock().instant();
    Tables.Factor row = Tables.Factor.newBuilder().setUid(UUID.randomUUID().toString()).setName(FACTORS + id)
        .setParent(account.name()).setLabels("{}").setRevision(1).setCreateTime(now.toString())
        .setUpdateTime(now.toString()).setPurgeTime(now.plus(options().flowLifetime()).toString())
        .setKind(kind.name()).setState(FactorImpl.PENDING).setSecret(ByteString.copyFrom(secret))
        .setCredentialId(credentialId).setCounter(counter).setUserVerified(userVerified).build();
    return factors.insert(row).map(ignored -> factor(row));
  }

  Future<Session> issue(Account account) {
    SessionToken token = SessionToken.generate();
    Instant now = schema.clock().instant();
    return macKey().compose(key -> key.sign(SessionImpl.data(token.secret()))).compose(mac -> {
      Tables.Session row = Tables.Session.newBuilder().setUid(UUID.randomUUID().toString())
          .setName(SESSIONS + token.id()).setParent(account.name()).setLabels("{}").setRevision(1)
          .setCreateTime(now.toString()).setUpdateTime(now.toString()).setTokenMac(mac.getSignature())
          .setExpireTime(now.plus(options().sessionLifetime()).toString())
          .setIdleExpireTime(now.plus(options().idleLifetime()).toString()).build();
      return sessions.insert(row).map(ignored -> (Session) new SessionImpl(this, row, Optional.of(token)));
    });
  }

  Future<List<Session>> sessions(String account) {
    return sessions.children(account).map(rows -> rows.stream()
        .<Session> map(row -> new SessionImpl(this, row, Optional.empty())).toList());
  }

  private Optional<DeclaredKind> kind(String kind, String value) {
    return kind.isEmpty() ? schema.types().identifierKindOf(value) : schema.types().identifierKind(kind);
  }

  private Future<Optional<Identifier>> identifier(String kind, String normalized) {
    return identifiers.find(and(eq("kind", kind), eq("value", normalized), isNull("delete_time"))).first()
        .map(found -> found.map(row -> new IdentifierImpl(this, row)));
  }
}
