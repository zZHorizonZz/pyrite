package dev.horizon.pyrite.flow;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.CodeAnswer;
import dev.horizon.pyrite.authentication.v1.CodeSent;
import dev.horizon.pyrite.authentication.v1.Delivery;
import io.vertx.core.Future;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Optional;

/**
 * The code of six digits that a flow sends to its identifier, which proves that the caller receives messages there.
 * The flow keeps a MAC of the code, bound to the flow, so a code of another flow is wrong. Only an identifier whose
 * kind receives codes, and that is verified or is the input of the flow with its account, gets one; any other flow
 * makes no code, and every answer is wrong.
 */
final class CodeAuthentication extends Authentication<CodeAnswer> {

  private static final int BOUND = 1_000_000;
  private static final SecureRandom RANDOM = new SecureRandom();

  CodeAuthentication() {
    super(CodeAnswer.getDefaultInstance());
  }

  @Override
  Future<Optional<Delivery>> prepare(Flow flow) {
    return flow.recipient().compose(identifier -> {
      if (identifier.isEmpty()) {
        return Future.succeededFuture(Optional.empty());
      }
      String code = String.format("%06d", RANDOM.nextInt(BOUND));
      return flow.type().macKey().compose(key -> key.sign(data(flow, code))).map(mac -> {
        flow.code(mac.getSignature().toByteArray());
        return Optional.of(Delivery.newBuilder().setKind(identifier.get().kind())
            .setIdentifier(identifier.get().name()).setTo(identifier.get().value()).setCode(code).build());
      });
    });
  }

  @Override
  Future<Challenge> challenge(Flow flow) {
    Challenge.Builder challenge = Challenge.newBuilder().setMethod(name());
    return flow.recipient().map(identifier -> identifier.map(found -> challenge.setParameters(Any.pack(
        CodeSent.newBuilder().setIdentifier(found.name()).setKind(found.kind()).build())).build())
        .orElse(challenge.build()));
  }

  @Override
  Future<Optional<Evidence>> evaluate(Flow flow, CodeAnswer answer) {
    String code = answer.getCode().strip();
    return flow.code().compose(mac -> mac.isEmpty() ? Future.succeededFuture(Optional.empty())
        : flow.type().macKey().compose(key -> key.verify(data(flow, code), mac.get(), "")).map(verdict -> {
          if (!verdict.hasVerified()) {
            return Optional.empty();
          }
          flow.clearCode();
          return Optional.of(Evidence.of(Evidence.Proof.IDENTIFIER));
        }));
  }

  private static byte[] data(Flow flow, String code) {
    return ("code\0" + flow.id() + ":" + code).getBytes(StandardCharsets.UTF_8);
  }
}
