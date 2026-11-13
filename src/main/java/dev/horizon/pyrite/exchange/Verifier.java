package dev.horizon.pyrite.exchange;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.Delivery;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Optional;

/**
 * One kind of answer that a stage takes, named by its message, such as {@code pyrite.authentication.v1.PasswordAnswer}:
 * what it prepares when the exchange reaches the stage, such as a code to deliver, the challenge that the response
 * names, and the evidence that a right answer gives. A verifier is built once, with the services that it uses, and
 * serves every exchange; what it keeps between the stage and the answer, it keeps in the exchange.
 *
 * @param <A> the message of the answer
 */
sealed interface Verifier<A extends Message> permits FactorVerifier, CodeVerifier, ProviderChallenge, Enrolling {

  /**
   * Gives an empty answer of the message that the verifier takes.
   */
  A prototype();

  /**
   * Gives the most that a right answer proves, which tells the stages that the verifier adds to.
   */
  Evidence proves();

  /**
   * Gives the full name of the message of the answer, which the challenge names.
   */
  default String name() {
    return prototype().getDescriptorForType().getFullName();
  }

  default boolean answers(Any answer) {
    return answer.getTypeUrl().endsWith("/" + name());
  }

  /**
   * Prepares the stage, once, when the exchange reaches it, such as by making a code to deliver.
   *
   * @return the code to deliver, or nothing
   */
  default Future<Optional<Delivery>> prepare(ExchangeRecord exchange) {
    return Future.succeededFuture(Optional.empty());
  }

  /**
   * Gives the challenge that the response names for this answer, with the parameters that the client needs, such as
   * the options of a passkey.
   */
  default Future<Challenge> challenge(ExchangeRecord exchange) {
    return Future.succeededFuture(Challenge.newBuilder().setMethod(name()).build());
  }

  /**
   * Weighs an answer.
   *
   * @param exchange the exchange, at the stage that the answer answers
   * @param answer the answer, parsed
   * @return the evidence of a right answer, or nothing for a wrong one
   */
  Future<Optional<Evidence>> verify(ExchangeRecord exchange, A answer);

  /**
   * Parses an answer, and weighs it.
   *
   * @return the evidence of a right answer, nothing for a wrong one, or a failure with {@code INVALID_ARGUMENT} for an
   * answer that does not parse
   */
  @SuppressWarnings("unchecked")
  default Future<Optional<Evidence>> accept(ExchangeRecord exchange, Any answer) {
    A parsed;
    try {
      parsed = (A) prototype().getParserForType().parseFrom(answer.getValue());
    } catch (InvalidProtocolBufferException malformed) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The answer is not a valid " + name()
          + "."));
    }
    return verify(exchange, parsed);
  }
}
