package dev.horizon.pyrite.flow;

import dev.horizon.pyrite.authentication.v1.Result;
import dev.horizon.pyrite.identity.Account;
import dev.horizon.pyrite.identity.Identifier;
import io.vertx.core.Future;

/**
 * Marks the identifier that a flow proved as verified.
 */
final class IdentifierVerification implements Effect {

  @Override
  public Future<Void> apply(Flow flow, Account account, Result.Builder result) {
    return flow.requireIdentifier().compose(Identifier::verify).map(identifier -> {
      result.addIdentifiers(identifier.message());
      return null;
    });
  }
}
