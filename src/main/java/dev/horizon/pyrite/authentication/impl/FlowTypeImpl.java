package dev.horizon.pyrite.authentication.impl;

import dev.horizon.cinder.Database;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.PyriteServiceOptions;
import dev.horizon.pyrite.authentication.v1.StartFlowRequest;
import dev.horizon.pyrite.impl.resource.Schema;
import dev.horizon.pyrite.impl.resource.Subject;
import dev.horizon.pyrite.impl.storage.FlowBackend;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.schema.v1.FlowDefinition;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Optional;
import java.util.UUID;

public final class FlowTypeImpl implements FlowType {

  static final String FLOWS = "flows/";

  private record Found(Optional<Account> account, Optional<Identifier> identifier) {
  }

  private final Schema schema;
  private final FlowBackend backend;

  public FlowTypeImpl(Schema schema, Database database) {
    this.schema = schema;
    this.backend = new FlowBackend(database);
  }

  @Override
  public Future<dev.horizon.pyrite.authentication.v1.Flow> start(StartFlowRequest request) {
    FlowDefinition definition = definition(request.getDefinition());
    return find(definition, request).compose(found -> {
      Tables.Flow.Builder row = Tables.Flow.newBuilder().setId(UUID.randomUUID().toString())
          .setDefinition(definition.getName()).setRevision(1).setProofs(Evidence.NOTHING.json().encode())
          .setExpireTime(schema.clock().instant().plus(options().flowLifetime()).toString());
      found.account().map(Account::name).ifPresent(row::setAccount);
      found.identifier().map(Identifier::name).ifPresent(row::setIdentifier);
      return new FlowImpl(this, definition, row).enter();
    });
  }

  @Override
  public Future<Flow> get(String name) {
    if (!name.startsWith(FLOWS)) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The flow " + name + " is not a name of the form"
          + " flows/{flow}.");
    }
    return backend.findById(name.substring(FLOWS.length())).map(found -> found
        .map(row -> new FlowImpl(this, definition(row.getDefinition()), row.toBuilder()))
        .orElseThrow(() -> new PyriteException(GrpcStatus.NOT_FOUND, "The flow " + name + " does not exist.")));
  }

  Schema schema() {
    return schema;
  }

  FlowBackend backend() {
    return backend;
  }

  AccountTypeImpl accounts() {
    return schema.accounts();
  }

  PyriteServiceOptions.Authentication options() {
    return accounts().options();
  }

  WebAuthn webAuthn() {
    return accounts().webAuthn();
  }

  private FlowDefinition definition(String name) {
    return schema.types().flow(name).orElseThrow(() -> new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The flow "
        + name + " is not declared."));
  }

  private Future<Found> find(FlowDefinition definition, StartFlowRequest request) {
    return switch (definition.getInput()) {
      case IDENTIFIER -> identifier(request).compose(identifier -> identifier.isEmpty()
          ? Future.succeededFuture(new Found(Optional.empty(), Optional.empty()))
          : accounts().account(identifier.get().account()).map(account -> new Found(Optional.of(account), identifier))
              .recover(failure -> failure instanceof PyriteException refusal
                  && refusal.status() == GrpcStatus.NOT_FOUND
                      ? Future.succeededFuture(new Found(Optional.empty(), identifier))
                      : Future.failedFuture(failure)));
      case SUBJECT -> accounts().account(Subject.parse(request.getSubject()))
          .map(account -> new Found(Optional.of(account), Optional.empty()));
      case SUBJECT_IDENTIFIER -> accounts().account(Subject.parse(request.getSubject()))
          .compose(account -> identifier(request).map(identifier -> {
            if (identifier.isEmpty() || !identifier.get().account().equals(account.name())) {
              throw new PyriteException(GrpcStatus.NOT_FOUND, "The account " + account.name() + " has no "
                  + request.getIdentifierKind() + " " + request.getIdentifier() + ".");
            }
            return new Found(Optional.of(account), identifier);
          }));
      case INPUT_UNSPECIFIED, UNRECOGNIZED -> throw new IllegalStateException("The flow " + definition.getName()
          + " has no input, which its declaration was checked for.");
    };
  }

  private Future<Optional<Identifier>> identifier(StartFlowRequest request) {
    if (request.getIdentifier().isBlank()) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The flow " + request.getDefinition() + " starts from"
          + " an identifier, and the request names none.");
    }
    return accounts().find(request.getIdentifierKind(), request.getIdentifier());
  }
}
