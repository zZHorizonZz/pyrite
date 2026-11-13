package dev.horizon.pyrite.identity;

import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
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
import dev.horizon.pyrite.authentication.v1.PurgeRequest;
import dev.horizon.pyrite.authentication.v1.PurgeResponse;
import dev.horizon.pyrite.authentication.v1.RefreshSessionRequest;
import dev.horizon.pyrite.authentication.v1.StartExchangeRequest;
import dev.horizon.pyrite.authentication.v1.TokenSet;
import dev.horizon.pyrite.deployment.Deployment;
import dev.horizon.pyrite.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;

/**
 * The service {@code pyrite.authentication.v1.Authentication}: accounts, their identifiers and sessions, and the
 * exchanges that sign people in.
 *
 * <p>
 * Each call reads the latest declaration, and checks a relation on the account that it names: {@code viewer} to read
 * the account, its identifiers or a session, and {@code admin} to change them. {@code CreateAccount}, the exchanges and
 * {@code Authenticate} check none. A call fails with the status of its {@link PyriteException}, and with
 * {@code INTERNAL} for any other failure.
 */
public final class AuthenticationEndpoint extends AuthenticationGrpcService {

  private final Deployment deployment;

  public AuthenticationEndpoint(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  public Future<dev.horizon.pyrite.authentication.v1.Account> createAccount(CreateAccountRequest request) {
    return deployment.schema().compose(schema -> schema.accounts().create(Subject.parse(request.getSubject()),
        request.getAccountId(), request.getKind())).map(Account::message).recover(PyriteException::fail);
  }

  @Override
  public Future<dev.horizon.pyrite.authentication.v1.Account> getAccount(GetAccountRequest request) {
    return deployment.schema().compose(schema -> schema.accounts().account(request.getName()))
        .compose(account -> account.resource().require(Subject.parse(request.getSubject()), "viewer",
            Struct.getDefaultInstance()).map(account))
        .map(Account::message).recover(PyriteException::fail);
  }

  @Override
  public Future<Empty> deleteAccount(DeleteAccountRequest request) {
    return deployment.schema().compose(schema -> schema.accounts().account(request.getName())
        .compose(account -> account.resource().require(Subject.parse(request.getSubject()), "admin",
            Struct.getDefaultInstance()).map(account))
        .compose(account -> schema.accounts().delete(account)))
        .map(Empty.getDefaultInstance()).recover(PyriteException::fail);
  }

  @Override
  public Future<dev.horizon.pyrite.authentication.v1.Identifier> addIdentifier(AddIdentifierRequest request) {
    return deployment.schema().compose(schema -> schema.accounts().account(request.getAccount())
        .compose(account -> account.resource().require(Subject.parse(request.getSubject()), "admin",
            Struct.getDefaultInstance()).map(account))
        .compose(account -> schema.accounts().identifiers().add(account.name(), request.getKind(),
            request.getValue(), request.getPrimary())))
        .map(Identifier::message).recover(PyriteException::fail);
  }

  @Override
  public Future<ListIdentifiersResponse> listIdentifiers(ListIdentifiersRequest request) {
    return deployment.schema().compose(schema -> schema.accounts().account(request.getAccount()))
        .compose(account -> account.resource().require(Subject.parse(request.getSubject()), "viewer",
            Struct.getDefaultInstance()).map(account))
        .compose(Account::identifiers)
        .map(identifiers -> ListIdentifiersResponse.newBuilder()
            .addAllIdentifiers(identifiers.stream().map(Identifier::message).toList()).build())
        .recover(PyriteException::fail);
  }

  @Override
  public Future<dev.horizon.pyrite.authentication.v1.Session> getSession(GetSessionRequest request) {
    return deployment.schema().compose(schema -> schema.accounts().sessions().session(request.getName())
        .compose(session -> schema.accounts().account(session.account())
            .compose(account -> account.resource().require(Subject.parse(request.getSubject()), "viewer",
                Struct.getDefaultInstance()))
            .map(allowed -> session.message())))
        .recover(PyriteException::fail);
  }

  @Override
  public Future<Empty> endSession(EndSessionRequest request) {
    return deployment.schema().compose(schema -> schema.accounts().sessions().session(request.getName())
        .compose(session -> schema.accounts().account(session.account())
            .compose(account -> account.resource().require(Subject.parse(request.getSubject()), "admin",
                Struct.getDefaultInstance()))
            .compose(allowed -> session.end())))
        .map(Empty.getDefaultInstance()).recover(PyriteException::fail);
  }

  @Override
  public Future<PurgeResponse> purge(PurgeRequest request) {
    return deployment.schema().compose(schema -> schema.accounts().purge())
        .map(count -> PurgeResponse.newBuilder().setPurged(count).build()).recover(PyriteException::fail);
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
      case ACCESS_TOKEN -> schema.accounts().authenticate(request.getAccessToken());
      case CREDENTIAL_NOT_SET -> throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The request names no"
          + " credential.");
    }).recover(PyriteException::fail);
  }

  @Override
  public Future<TokenSet> refreshSession(RefreshSessionRequest request) {
    return deployment.schema().compose(schema -> schema.accounts().refresh(request.getRefreshToken()))
        .recover(PyriteException::fail);
  }

  @Override
  public Future<ListFactorsResponse> listFactors(ListFactorsRequest request) {
    return deployment.schema().compose(schema -> schema.accounts().account(request.getAccount()))
        .compose(account -> account.resource().require(Subject.parse(request.getSubject()), "viewer",
            Struct.getDefaultInstance()).map(account))
        .compose(Account::credentials)
        .map(credentials -> ListFactorsResponse.newBuilder().addAllFactors(credentials.stream()
            .filter(Credential::isActive).map(Credential::message).toList()).build())
        .recover(PyriteException::fail);
  }

  @Override
  public Future<Empty> deleteFactor(DeleteFactorRequest request) {
    return deployment.schema().compose(schema -> schema.accounts().credentials().credential(request.getName())
        .compose(credential -> schema.accounts().account(credential.account())
            .compose(account -> account.resource().require(Subject.parse(request.getSubject()), "admin",
                Struct.getDefaultInstance()))
            .compose(allowed -> credential.isActive() ? schema.accounts().credentials().delete(credential)
                : Future.failedFuture(new PyriteException(GrpcStatus.NOT_FOUND, "The factor " + request.getName()
                    + " is removed.")))))
        .map(Empty.getDefaultInstance()).recover(PyriteException::fail);
  }
}
