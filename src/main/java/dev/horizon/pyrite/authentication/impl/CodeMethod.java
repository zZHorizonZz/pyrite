package dev.horizon.pyrite.authentication.impl;

import com.google.protobuf.Any;
import com.google.protobuf.Timestamp;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.CodeAnswer;
import dev.horizon.pyrite.authentication.v1.CodeSent;
import dev.horizon.pyrite.authentication.v1.Delivery;
import io.vertx.core.Future;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

final class CodeMethod extends Method<CodeAnswer> {

  private static final int BOUND = 1_000_000;
  private static final SecureRandom RANDOM = new SecureRandom();

  CodeMethod() {
    super(CodeAnswer.getDefaultInstance());
  }

  @Override
  Future<Optional<Delivery>> prepare(FlowImpl flow) {
    return flow.recipient().compose(identifier -> {
      if (identifier.isEmpty()) {
        return Future.succeededFuture(Optional.empty());
      }
      String code = String.format("%06d", RANDOM.nextInt(BOUND));
      Instant expires = flow.type().schema().clock().instant().plus(flow.type().options().codeLifetime());
      return flow.type().accounts().macKey().compose(key -> key.sign(data(flow, code))).map(mac -> {
        flow.code(mac.getSignature().toByteArray(), expires);
        return Optional.of(Delivery.newBuilder().setChannel(identifier.get().channel())
            .setTo(identifier.get().value()).setCode(code)
            .setExpireTime(Timestamp.newBuilder().setSeconds(expires.getEpochSecond()).setNanos(expires.getNano()))
            .build());
      });
    });
  }

  @Override
  Future<Challenge> challenge(FlowImpl flow) {
    Challenge.Builder challenge = Challenge.newBuilder().setMethod(name());
    return flow.recipient().map(identifier -> identifier.map(found -> challenge.setParameters(Any.pack(
        CodeSent.newBuilder().setChannel(found.channel()).setTo(found.mask()).build())).build())
        .orElse(challenge.build()));
  }

  @Override
  Future<Optional<Evidence>> evaluate(FlowImpl flow, CodeAnswer answer) {
    String code = answer.getCode().strip();
    Optional<byte[]> mac = flow.code();
    if (mac.isEmpty()) {
      return Future.succeededFuture(Optional.empty());
    }
    return flow.type().accounts().macKey().compose(key -> key.verify(data(flow, code), mac.get(), ""))
        .map(verdict -> {
          if (!verdict.hasVerified()) {
            return Optional.empty();
          }
          flow.clearCode();
          return Optional.of(Evidence.of(Evidence.Proof.IDENTIFIER));
        });
  }

  private static byte[] data(FlowImpl flow, String code) {
    return ("code\0" + flow.id() + ":" + code).getBytes(StandardCharsets.UTF_8);
  }
}
