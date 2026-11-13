package dev.horizon.pyrite.exchange;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import dev.horizon.pyrite.AuthenticationOptions;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.CodeAnswer;
import dev.horizon.pyrite.authentication.v1.CodeSent;
import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.key.KeyType;
import dev.horizon.pyrite.key.SigningKey;
import io.vertx.core.Future;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Optional;

/**
 * The code of six digits that an exchange sends to its identifier, which proves that the caller receives messages
 * there. The exchange keeps a MAC of the code, bound to the exchange, and the code lasts as long as the nonce of the
 * stage. Only an identifier whose kind receives codes, and that is verified or is the input of the exchange with its
 * account, gets one; any other exchange makes no code, and every answer is wrong.
 */
final class CodeVerifier implements Verifier<CodeAnswer> {

  private static final int BOUND = 1_000_000;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final KeyType keys;
  private final AuthenticationOptions options;

  CodeVerifier(KeyType keys, AuthenticationOptions options) {
    this.keys = keys;
    this.options = options;
  }

  @Override
  public CodeAnswer prototype() {
    return CodeAnswer.getDefaultInstance();
  }

  @Override
  public Evidence proves() {
    return Evidence.IDENTIFIER;
  }

  @Override
  public Future<Optional<Delivery>> prepare(Exchange exchange) {
    return exchange.claimant().recipient().compose(identifier -> {
      if (identifier.isEmpty()) {
        return Future.succeededFuture(Optional.empty());
      }
      String code = String.format("%06d", RANDOM.nextInt(BOUND));
      return macKey().compose(key -> key.sign(data(exchange, code))).map(mac -> {
        exchange.keep(this, mac.getSignature());
        return Optional.of(Delivery.newBuilder().setKind(identifier.get().kind())
            .setIdentifier(identifier.get().name()).setTo(identifier.get().value()).setCode(code).build());
      });
    });
  }

  @Override
  public Future<Challenge> challenge(Exchange exchange) {
    Challenge.Builder challenge = Challenge.newBuilder().setMethod(name());
    return exchange.claimant().recipient().map(identifier -> identifier.map(found -> challenge.setParameters(Any.pack(
        CodeSent.newBuilder().setIdentifier(found.name()).setKind(found.kind()).build())).build())
        .orElse(challenge.build()));
  }

  @Override
  public Future<Optional<Evidence>> verify(Exchange exchange, CodeAnswer answer) {
    Optional<ByteString> mac = exchange.nonce().flatMap(nonce -> exchange.state(this));
    if (mac.isEmpty()) {
      return Future.succeededFuture(Optional.empty());
    }
    String code = answer.getCode().strip();
    return macKey().compose(key -> key.verify(data(exchange, code), mac.get().toByteArray(), "")).map(verdict -> {
      if (!verdict.hasVerified()) {
        return Optional.empty();
      }
      exchange.forget(this);
      return Optional.of(Evidence.IDENTIFIER);
    });
  }

  private Future<SigningKey> macKey() {
    return keys.signing(options.getMacKey());
  }

  private static byte[] data(Exchange exchange, String code) {
    return ("code\0" + exchange.id() + ":" + code).getBytes(StandardCharsets.UTF_8);
  }
}
