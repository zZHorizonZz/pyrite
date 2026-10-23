package dev.horizon.pyrite.authentication.impl;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.Delivery;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Optional;

abstract sealed class Method<A extends Message> permits PasswordMethod, TotpMethod, RecoveryCodeMethod,
    PasskeyMethod, CodeMethod, PasswordEnrolment, TotpEnrolment, PasskeyEnrolment {

  private final A prototype;

  Method(A prototype) {
    this.prototype = prototype;
  }

  String name() {
    return prototype.getDescriptorForType().getFullName();
  }

  boolean answers(Any answer) {
    return answer.getTypeUrl().endsWith("/" + name());
  }

  Future<Optional<Delivery>> prepare(FlowImpl flow) {
    return Future.succeededFuture(Optional.empty());
  }

  Future<Challenge> challenge(FlowImpl flow) {
    return Future.succeededFuture(Challenge.newBuilder().setMethod(name()).build());
  }

  final Future<Optional<Evidence>> accept(FlowImpl flow, Any answer) {
    return evaluate(flow, unpack(answer));
  }

  abstract Future<Optional<Evidence>> evaluate(FlowImpl flow, A answer);

  @SuppressWarnings("unchecked")
  private A unpack(Any answer) {
    try {
      return (A) prototype.getParserForType().parseFrom(answer.getValue());
    } catch (InvalidProtocolBufferException malformed) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The answer is not a valid " + name() + ".");
    }
  }
}
