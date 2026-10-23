package dev.horizon.pyrite.authentication.impl;

import dev.horizon.cinder.Database;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.impl.model.DeclaredType;
import dev.horizon.pyrite.impl.model.TypeSystem;
import dev.horizon.pyrite.impl.resource.BaseResourceType;
import dev.horizon.pyrite.impl.resource.Resource;
import dev.horizon.pyrite.impl.resource.SchemaImpl;
import dev.horizon.pyrite.impl.resource.Subject;
import dev.horizon.pyrite.impl.storage.Row;
import dev.horizon.pyrite.schema.v1.Stage;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FactorTypeImpl extends BaseResourceType<Factor> implements FactorType {

  private final WebAuthn webAuthn;

  public FactorTypeImpl(DeclaredType declared, SchemaImpl schema, Database database) {
    super(declared, schema, database, Set.of(KIND, STATE, SECRET, CREDENTIAL_ID,
        COUNTER, USER_VERIFIED), Set.of(SECRET), Set.of(), true);
    this.webAuthn = new WebAuthn(schema.options().authentication().orElseThrow().relyingParty());
  }

  @Override
  protected Factor resource(Row row) {
    return switch (Stage.Factor.valueOf((String) row.get(KIND))) {
      case PASSWORD -> new PasswordFactor(this, row);
      case TOTP -> new TotpFactor(this, row);
      case PASSKEY -> new PasskeyFactor(this, row);
      case RECOVERY_CODES -> new RecoveryCodesFactor(this, row);
      case FACTOR_UNSPECIFIED, UNRECOGNIZED -> throw new IllegalStateException("The factor " + row.get(Resource.NAME)
          + " has no kind.");
    };
  }

  @Override
  public Future<Factor> create(Subject creator, String parent, String id, Map<String, Object> values) {
    throw new PyriteException(GrpcStatus.FAILED_PRECONDITION, "Factors are enrolled by flows of"
        + " pyrite.authentication.v1.Authentication, not created.");
  }

  WebAuthn webAuthn() {
    return webAuthn;
  }

  AccountTypeImpl accounts() {
    return schema().type(TypeSystem.ACCOUNT, AccountTypeImpl.class);
  }

  Future<Factor> enrol(AccountImpl account, String id, Stage.Factor kind, byte[] secret, String credentialId,
      long counter, boolean userVerified) {
    Map<String, Object> values = new HashMap<>();
    values.put(KIND, kind.name());
    values.put(STATE, FactorImpl.PENDING);
    values.put(SECRET, secret);
    values.put(CREDENTIAL_ID, credentialId);
    values.put(COUNTER, counter);
    values.put(USER_VERIFIED, userVerified);
    values.put(Resource.PURGE_TIME, schema().clock().instant().plus(accounts().options().flowLifetime()));
    return insert(Optional.of(account), id, values);
  }
}
