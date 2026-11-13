package dev.horizon.pyrite.conformance.exchange;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerExchangeByName;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;
import java.util.Set;

@Summary("An unknown exchange is not found, and a name that is not an exchange's is refused.")
final class ExchangeName extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    for (String unknown : List.of("exchanges/00000000-0000-0000-0000-000000000000", "exchanges/nope")) {
      callAndContinueOnFailure(new ExpectStatus(Set.of(GrpcStatus.NOT_FOUND, GrpcStatus.INVALID_ARGUMENT),
          new AnswerExchangeByName(unknown, PASSWORD)), Requirement.EXCHANGE_NAME);
    }
    for (String name : List.of("", "sessions/abc", "accounts/anne", "exchanges")) {
      callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new AnswerExchangeByName(name, PASSWORD)),
          Requirement.EXCHANGE_NAME);
    }
  }
}
