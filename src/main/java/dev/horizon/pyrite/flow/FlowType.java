package dev.horizon.pyrite.flow;

import dev.horizon.cinder.Database;
import dev.horizon.pyrite.AuthenticationOptions;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.authentication.v1.StartFlowRequest;
import dev.horizon.pyrite.declaration.TypeSystem;
import dev.horizon.pyrite.identity.Account;
import dev.horizon.pyrite.identity.AccountType;
import dev.horizon.pyrite.identity.CredentialType;
import dev.horizon.pyrite.identity.Identifier;
import dev.horizon.pyrite.identity.IdentifierType;
import dev.horizon.pyrite.identity.WebAuthn;
import dev.horizon.pyrite.key.KeyType;
import dev.horizon.pyrite.key.SigningKey;
import dev.horizon.pyrite.resource.Subject;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.schema.v1.FlowDefinition;
import dev.horizon.pyrite.storage.FlowBackend;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Optional;
import java.util.UUID;

/**
 * The flows of the declaration: it starts a flow of a declared definition, from an identifier, a subject or both, and
 * finds a flow that is under way. A flow that starts from an identifier that no account has does not fail; it takes
 * the same answers as any other, none of them right, so that a caller learns nothing of which accounts exist.
 */
public final class FlowType {

  static final String FLOWS = "flows/";

  private record Origin(Optional<Account> account, Optional<Identifier> identifier) {
  }

  private final FlowBackend backend;
  private final TypeSystem types;
  private final Vertx vertx;
  private final AuthenticationOptions options;
  private final AccountType accounts;
  private final KeyType keys;
  private final FlowInterceptor interceptor;

  public FlowType(Database database, TypeSystem types, Vertx vertx,
      AuthenticationOptions options, AccountType accounts, KeyType keys,
      FlowInterceptor interceptor) {
    this.backend = new FlowBackend(database);
    this.types = types;
    this.vertx = vertx;
    this.options = options;
    this.accounts = accounts;
    this.keys = keys;
    this.interceptor = interceptor;
  }

  /**
   * Starts a flow of a declared definition, after the interceptors of the start, and gives the codes of its first
   * stage to the interceptors of codes, or in the response when there are none.
   *
   * @param request the definition, and the subject, the identifier, or both, that its input takes
   * @return the flow, at its first stage, or done if it needs no answer; or a failure with {@code INVALID_ARGUMENT}
   * for a definition that is not declared, a missing identifier or a subject that is not an account, or with
   * {@code NOT_FOUND} for a subject without an account, or an identifier that is not of the subject's account
   */
  public Future<dev.horizon.pyrite.authentication.v1.Flow> start(StartFlowRequest request) {
    return definition(request.getDefinition()).compose(definition -> origin(definition, request).compose(origin -> {
      Tables.Flow.Builder row = Tables.Flow.newBuilder().setId(UUID.randomUUID().toString())
          .setDefinition(definition.getName()).setRevision(1).setProofs(Evidence.NOTHING.json().encode());
      origin.account().map(Account::name).ifPresent(row::setAccount);
      origin.identifier().map(Identifier::name).ifPresent(row::setIdentifier);
      Flow flow = new Flow(this, definition, row, true);
      return interceptor.start(flow, origin.account()).compose(ignored -> flow.enter());
    }));
  }

  /**
   * Finds a flow of a name, expired or not; an expired flow refuses every answer.
   *
   * @param name the name, such as {@code flows/9f1c2a64-7d1e-4f0a-9a43-1f6c0e2b8d55}
   * @return the flow, or a failure with {@code INVALID_ARGUMENT} for a name that is not of the form
   * {@code flows/{flow}}, or with {@code NOT_FOUND}
   */
  public Future<Flow> flow(String name) {
    if (!name.startsWith(FLOWS)) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The flow " + name + " is not a"
          + " name of the form flows/{flow}."));
    }
    String id = name.substring(FLOWS.length());
    return backend.findLive(id).compose(live -> live.isPresent() ? flow(live.get(), true)
        : backend.findById(id).compose(expired -> expired.isEmpty()
            ? Future.failedFuture(new PyriteException(GrpcStatus.NOT_FOUND, "The flow " + name + " does not exist."))
            : flow(expired.get(), false)));
  }

  private Future<Flow> flow(Tables.Flow row, boolean live) {
    return definition(row.getDefinition()).map(definition -> new Flow(this, definition, row.toBuilder(), live));
  }

  FlowInterceptor interceptor() {
    return interceptor;
  }

  FlowBackend backend() {
    return backend;
  }

  Vertx vertx() {
    return vertx;
  }

  AuthenticationOptions options() {
    return options;
  }

  AccountType accounts() {
    return accounts;
  }

  IdentifierType identifiers() {
    return accounts.identifiers();
  }

  CredentialType credentials() {
    return accounts.credentials();
  }

  WebAuthn webAuthn() {
    return accounts.credentials().webAuthn();
  }

  Future<SigningKey> macKey() {
    return keys.signing(options.getMacKey());
  }

  private Future<FlowDefinition> definition(String name) {
    return types.flow(name).map(Future::succeededFuture).orElseGet(() -> Future.failedFuture(new PyriteException(
        GrpcStatus.INVALID_ARGUMENT, "The flow " + name + " is not declared.")));
  }

  private Future<Origin> origin(FlowDefinition definition, StartFlowRequest request) {
    return switch (definition.getInput()) {
      case IDENTIFIER -> identifier(request).compose(identifier -> identifier.isEmpty()
          ? Future.succeededFuture(new Origin(Optional.empty(), Optional.empty()))
          : accounts.account(identifier.get().account()).map(account -> new Origin(Optional.of(account), identifier))
              .recover(failure -> failure instanceof PyriteException refusal
                  && refusal.status() == GrpcStatus.NOT_FOUND
                      ? Future.succeededFuture(new Origin(Optional.empty(), identifier))
                      : Future.failedFuture(failure)));
      case SUBJECT -> accounts.account(Subject.parse(request.getSubject()))
          .map(account -> new Origin(Optional.of(account), Optional.empty()));
      case SUBJECT_IDENTIFIER -> accounts.account(Subject.parse(request.getSubject()))
          .compose(account -> identifier(request).compose(identifier -> identifier.isEmpty()
              || !identifier.get().account().equals(account.name())
                  ? Future.failedFuture(new PyriteException(GrpcStatus.NOT_FOUND, "The account " + account.name()
                      + " has no " + request.getIdentifierKind() + " " + request.getIdentifier() + "."))
                  : Future.succeededFuture(new Origin(Optional.of(account), identifier))));
      case INPUT_UNSPECIFIED, UNRECOGNIZED -> Future.failedFuture(new IllegalStateException("The flow "
          + definition.getName() + " has no input, which its declaration was checked for."));
    };
  }

  private Future<Optional<Identifier>> identifier(StartFlowRequest request) {
    if (request.getIdentifier().isBlank()) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The flow "
          + request.getDefinition() + " starts from an identifier, and the request names none."));
    }
    return accounts.identifiers().find(request.getIdentifierKind(), request.getIdentifier());
  }
}
