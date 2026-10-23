package dev.horizon.pyrite.authentication.impl;

import dev.horizon.cinder.Database;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.PyriteServiceOptions;
import dev.horizon.pyrite.crypto.impl.EncryptionKey;
import dev.horizon.pyrite.crypto.impl.KeyType;
import dev.horizon.pyrite.crypto.impl.SigningKey;
import dev.horizon.pyrite.impl.model.DeclaredType;
import dev.horizon.pyrite.impl.model.TypeSystem;
import dev.horizon.pyrite.impl.resource.BaseResourceType;
import dev.horizon.pyrite.impl.resource.Resource;
import dev.horizon.pyrite.impl.resource.SchemaImpl;
import dev.horizon.pyrite.impl.resource.Subject;
import dev.horizon.pyrite.impl.storage.Row;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class AccountTypeImpl extends BaseResourceType<Account> implements AccountType {

  public AccountTypeImpl(DeclaredType declared, SchemaImpl schema, Database database) {
    super(declared, schema, database, Set.of(), Set.of(), Set.of(KIND), false);
  }

  @Override
  protected Account resource(Row row) {
    return new AccountImpl(this, row);
  }

  @Override
  protected Future<Account> insert(Optional<Resource> parent, String id, Map<String, Object> values) {
    if (!KINDS.contains(values.get(KIND))) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "An account's kind is one of " + KINDS + ".");
    }
    return super.insert(parent, id, values).compose(account -> schema().adapter().write(TupleKey.newBuilder()
        .setUser(account.object()).setRelation(OWNER).setObject(account.object()).build()).map(account));
  }

  @Override
  public Future<Account> account(Subject subject) {
    if (!subject.type().equals(TypeSystem.USER)) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The subject " + subject + " is not an account.");
    }
    return schema().require(declaration().resource(subject.id())).map(found -> (Account) found.requireActive());
  }

  PyriteServiceOptions.Authentication options() {
    return schema().options().authentication().orElseThrow();
  }

  Future<SigningKey> macKey() {
    return schema().type(TypeSystem.KEY, KeyType.class).signing(options().macKey());
  }

  Future<EncryptionKey> encryptionKey() {
    return schema().type(TypeSystem.KEY, KeyType.class).encryption(options().encryptionKey());
  }

  IdentifierTypeImpl identifiers() {
    return schema().type(TypeSystem.IDENTIFIER, IdentifierTypeImpl.class);
  }

  FactorTypeImpl factors() {
    return schema().type(TypeSystem.FACTOR, FactorTypeImpl.class);
  }

  SessionTypeImpl sessions() {
    return schema().type(TypeSystem.SESSION, SessionTypeImpl.class);
  }
}
