package dev.horizon.pyrite.authentication.impl;

import com.google.protobuf.Struct;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.authentication.v1.Principal;
import dev.horizon.pyrite.impl.model.DeclaredType;
import dev.horizon.pyrite.impl.model.TypeSystem;
import dev.horizon.pyrite.impl.resource.BaseResourceType;
import dev.horizon.pyrite.impl.resource.SchemaImpl;
import dev.horizon.pyrite.impl.resource.Subject;
import dev.horizon.pyrite.impl.storage.Row;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SessionTypeImpl extends BaseResourceType<Session> implements SessionType {

  public SessionTypeImpl(DeclaredType declared, SchemaImpl schema, Database database) {
    super(declared, schema, database, Set.of(TOKEN_MAC, EXPIRE_TIME, IDLE_EXPIRE_TIME),
        Set.of(TOKEN_MAC), Set.of(), true);
  }

  @Override
  protected Session resource(Row row) {
    return new SessionImpl(this, row, Optional.empty());
  }

  @Override
  public Future<Session> create(Subject creator, String parent, String id, Map<String, Object> values,
      Struct context) {
    throw new PyriteException(GrpcStatus.FAILED_PRECONDITION, "Sessions are issued by flows of"
        + " pyrite.authentication.v1.Authentication, not created.");
  }

  @Override
  public Future<Principal> authenticate(String token) {
    SessionToken parsed = SessionToken.parse(token);
    return get(declaration().resource(parsed.id())).compose(found -> found
        .map(session -> session.authenticate(parsed.secret()))
        .orElseGet(() -> Future.failedFuture(new PyriteException(GrpcStatus.UNAUTHENTICATED, "The session has"
            + " ended."))));
  }

  Future<Session> issue(AccountImpl account) {
    SessionToken token = SessionToken.generate();
    Instant now = schema().clock().instant();
    return accounts().macKey().compose(key -> key.sign(SessionImpl.data(token.secret()))).compose(mac -> {
      Map<String, Object> values = new HashMap<>();
      values.put(TOKEN_MAC, mac.getSignature().toByteArray());
      values.put(EXPIRE_TIME, now.plus(accounts().options().sessionLifetime()));
      values.put(IDLE_EXPIRE_TIME, now.plus(accounts().options().idleLifetime()));
      return insert(Optional.of(account), token.id(), values);
    }).map(session -> ((SessionImpl) session).withToken(token));
  }

  AccountTypeImpl accounts() {
    return schema().type(TypeSystem.ACCOUNT, AccountTypeImpl.class);
  }
}
