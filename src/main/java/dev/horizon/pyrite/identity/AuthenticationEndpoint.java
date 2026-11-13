package dev.horizon.pyrite.identity;

import com.google.protobuf.Empty;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.authentication.v1.AddIdentifierRequest;
import dev.horizon.pyrite.authentication.v1.AuthenticateRequest;
import dev.horizon.pyrite.authentication.v1.AuthenticationGrpcService;
import dev.horizon.pyrite.authentication.v1.ContinueExchangeRequest;
import dev.horizon.pyrite.authentication.v1.CreateAccountRequest;
import dev.horizon.pyrite.authentication.v1.DeleteAccountRequest;
import dev.horizon.pyrite.authentication.v1.DeleteFactorRequest;
import dev.horizon.pyrite.authentication.v1.EndSessionRequest;
import dev.horizon.pyrite.authentication.v1.GetAccountRequest;
import dev.horizon.pyrite.authentication.v1.GetSessionRequest;
import dev.horizon.pyrite.authentication.v1.ListFactorsRequest;
import dev.horizon.pyrite.authentication.v1.ListFactorsResponse;
import dev.horizon.pyrite.authentication.v1.ListIdentifiersRequest;
import dev.horizon.pyrite.authentication.v1.ListIdentifiersResponse;
import dev.horizon.pyrite.authentication.v1.Principal;
import dev.horizon.pyrite.authentication.v1.RefreshSessionRequest;
import dev.horizon.pyrite.authentication.v1.StartExchangeRequest;
import dev.horizon.pyrite.authentication.v1.TokenSet;
import dev.horizon.pyrite.deployment.Deployment;
import dev.horizon.pyrite.resource.Subject;
import dev.horizon.pyrite.session.SessionService;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;

/**
 * The service {@code pyrite.authentication.v1.Authentication}: accounts, their identifiers and sessions, and the
 * exchanges that sign people in.
 *
 * <p>
 * Each call reads the latest declaration, and checks a relation on the account that it names: {@code viewer} to read
 * the account, its identifiers, factors or a session, and {@code admin} to change them. The relation is checked before
 * the account is read, and a call on a session or a factor that does not exist fails as one that the subject may not
 * make, so a subject learns nothing of what exists that it may not see. {@code CreateAccount}, the exchanges,
 * {@code Authenticate} and {@code RefreshSession} check none. A call fails with the status of its
 * {@link PyriteException}, and with {@code INTERNAL} for any other failure.
 */
public final class AuthenticationEndpoint extends AuthenticationGrpcService {

  private final Deployment deployment;

  public AuthenticationEndpoint(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  public Future<dev.horizon.pyrite.authentication.v1.Account> createAccount(CreateAccountRequest request) {
    return deployment.schema().compose(schema -> schema.accounts().create(Subject.parse(request.getSubject()),
        request.getAccountId(), request.getKind())).map(AccountService::message).recover(PyriteException::fail);
  }

  @Override
  public Future<dev.horizon.pyrite.authentication.v1.Account> getAccount(GetAccountRequest request) {
    return deployment.schema().compose(schema -> schema.accounts().account(request.getName(),
        Subject.parse(request.getSubject()), "viewer")).map(AccountService::message).recover(PyriteException::fail);
  }

  @Override
  public Future<Empty> deleteAccount(DeleteAccountRequest request) {
    return deployment.schema().compose(schema -> schema.accounts().account(request.getName(),
        Subject.parse(request.getSubject()), "admin").compose(account -> schema.accounts().delete(account.getName())))
        .map(Empty.getDefaultInstance()).recover(PyriteException::fail);
  }

  @Override
  public Future<dev.horizon.pyrite.authentication.v1.Identifier> addIdentifier(AddIdentifierRequest request) {
    return deployment.schema().compose(schema -> schema.accounts().account(request.getAccount(),
        Subject.parse(request.getSubject()), "admin")
        .compose(account -> schema.identifiers().add(account.getName(), request.getKind(), request.getValue(),
            request.getPrimary())))
        .map(IdentifierService::message).recover(PyriteException::fail);
  }

  @Override
  public Future<ListIdentifiersResponse> listIdentifiers(ListIdentifiersRequest request) {
    return deployment.schema().compose(schema -> schema.accounts().account(request.getAccount(),
        Subject.parse(request.getSubject()), "viewer")
        .compose(account -> schema.identifiers().identifiers(account.getName())))
        .map(identifiers -> ListIdentifiersResponse.newBuilder()
            .addAllIdentifiers(identifiers.stream().map(IdentifierService::message).toList()).build())
        .recover(PyriteException::fail);
  }

  @Override
  public Future<dev.horizon.pyrite.authentication.v1.Session> getSession(GetSessionRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    return deployment.schema().compose(schema -> hidden(schema.sessions().session(request.getName())
        .compose(session -> schema.accounts().account(session.getParent(), subject, "viewer")
            .map(allowed -> SessionService.message(session))),
        subject, "viewer", request.getName()))
        .recover(PyriteException::fail);
  }

  @Override
  public Future<Empty> endSession(EndSessionRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    return deployment.schema().compose(schema -> hidden(schema.sessions().session(request.getName())
        .compose(session -> schema.accounts().account(session.getParent(), subject, "admin")
            .compose(allowed -> schema.sessions().end(session))),
        subject, "admin", request.getName()))
        .map(Empty.getDefaultInstance()).recover(PyriteException::fail);
  }

  @Override
  public Future<dev.horizon.pyrite.authentication.v1.Exchange> startExchange(StartExchangeRequest request) {
    return deployment.schema().compose(schema -> schema.exchanges().start(request)).recover(PyriteException::fail);
  }

  @Override
  public Future<dev.horizon.pyrite.authentication.v1.Exchange> continueExchange(ContinueExchangeRequest request) {
    return deployment.schema().compose(schema -> schema.exchanges().answer(request)).recover(PyriteException::fail);
  }

  @Override
  public Future<Principal> authenticate(AuthenticateRequest request) {
    return deployment.schema().compose(schema -> switch (request.getCredentialCase()) {
      case ACCESS_TOKEN -> schema.sessions().authenticate(request.getAccessToken());
      case CREDENTIAL_NOT_SET -> throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The request names no"
          + " credential.");
    }).recover(PyriteException::fail);
  }

  @Override
  public Future<TokenSet> refreshSession(RefreshSessionRequest request) {
    return deployment.schema().compose(schema -> schema.sessions().refresh(request.getRefreshToken()))
        .recover(PyriteException::fail);
  }

  @Override
  public Future<ListFactorsResponse> listFactors(ListFactorsRequest request) {
    return deployment.schema().compose(schema -> schema.accounts().account(request.getAccount(),
        Subject.parse(request.getSubject()), "viewer")
        .compose(account -> schema.factors().factors(account.getName())))
        .map(factors -> ListFactorsResponse.newBuilder().addAllFactors(factors.stream()
            .filter(FactorService::isActive).map(FactorService::message).toList()).build())
        .recover(PyriteException::fail);
  }

  @Override
  public Future<Empty> deleteFactor(DeleteFactorRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    return deployment.schema().compose(schema -> hidden(schema.factors().factor(request.getName())
        .compose(factor -> schema.accounts().account(factor.getParent(), subject, "admin")),
        subject, "admin", request.getName())
        .compose(allowed -> schema.factors().delete(request.getName())))
        .map(Empty.getDefaultInstance()).recover(PyriteException::fail);
  }

  /**
   * Fails a call on a session or a factor the same way whether the name exists or the subject lacks the relation, so
   * that a subject without it learns nothing of whether the name exists.
   */
  private static <T> Future<T> hidden(Future<T> call, Subject subject, String relation, String name) {
    return call.recover(failure -> Future.failedFuture(failure instanceof PyriteException refusal
        && (refusal.status() == GrpcStatus.NOT_FOUND || refusal.status() == GrpcStatus.PERMISSION_DENIED)
            ? new PyriteException(GrpcStatus.PERMISSION_DENIED, "The subject " + subject + " has no " + relation
                + " on " + name + ".")
            : failure));
  }
}
