package dev.horizon.pyrite.flow;

import dev.horizon.pyrite.authentication.v1.Result;
import dev.horizon.pyrite.identity.Account;
import dev.horizon.pyrite.identity.Credential;
import io.vertx.core.Future;

/**
 * Activates the credential that a flow enrolled, or, for a flow that enrols recovery codes, creates and activates
 * them, and gives out the codes once.
 */
final class CredentialActivation implements Effect {

  @Override
  public Future<Void> apply(Flow flow, Account account, Result.Builder result) {
    if (flow.hasEnrolment()) {
      return flow.enrolment(Credential.class).compose(Credential::activate).map(active -> {
        result.addFactors(active.message());
        return null;
      });
    }
    return flow.type().credentials().createRecoveryCodes(account.name()).compose(codes -> codes.credential()
        .activate().map(active -> {
          result.addFactors(active.message()).addIssued(codes.toAny());
          return null;
        }));
  }
}
