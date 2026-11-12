package dev.horizon.pyrite.flow;

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
 * what it prepares when the flow reaches the stage, such as a code or a WebAuthn challenge, the challenge that the
 * response names, and the evidence that a right answer gives.
 *
 * @param <A> the message of the answer
 */
abstract sealed class Authentication<A extends Message>
    permits PasswordAuthentication, TotpAuthentication, RecoveryCodeAuthentication,
    PasskeyAuthentication, CodeAuthentication, PasswordEnrolment, TotpEnrolment, PasskeyEnrolment {

  private final A prototype;

  Authentication(A prototype) {
    this.prototype = prototype;
  }

  String name() {
    return prototype.getDescriptorForType().getFullName();
  }

  boolean answers(Any answer) {
    return answer.getTypeUrl().endsWith("/" + name());
  }

  Future<Optional<Delivery>> prepare(Flow flow) {
    return Future.succeededFuture(Optional.empty());
  }

  Future<Challenge> challenge(Flow flow) {
    return Future.succeededFuture(Challenge.newBuilder().setMethod(name()).build());
  }

  final Future<Optional<Evidence>> accept(Flow flow, Any answer) {
    A unpacked;
    try {
      unpacked = unpack(answer);
    } catch (PyriteException malformed) {
      return Future.failedFuture(malformed);
    }
    return evaluate(flow, unpacked);
  }

  /**
   * Weighs an answer.
   *
   * @param flow the flow, at the stage that the answer answers
   * @param answer the answer, parsed
   * @return the evidence of a right answer, or nothing for a wrong one
   */
  abstract Future<Optional<Evidence>> evaluate(Flow flow, A answer);

  @SuppressWarnings("unchecked")
  private A unpack(Any answer) {
    try {
      return (A) prototype.getParserForType().parseFrom(answer.getValue());
    } catch (InvalidProtocolBufferException malformed) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The answer is not a valid " + name() + ".");
    }
  }
}
