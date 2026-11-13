package dev.horizon.pyrite.exchange;

import com.google.protobuf.ByteString;
import dev.horizon.pyrite.AuthenticationOptions;
import dev.horizon.pyrite.authentication.v1.CodeAnswer;
import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.key.KeyService;
import io.vertx.core.Future;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Optional;

/**
 * The code of six digits that an exchange sends to its identifier, which proves that the caller receives messages
 * there. The exchange keeps a MAC of the code, bound to the exchange, and the code lasts as long as the nonce of the
 * stage. Only an identifier whose kind receives codes, and that is verified or is the input of the exchange with its
 * account, gets one; any other exchange makes no code, and every answer is wrong. The challenge is the same either
 * way, and Pyrite signs a code either way, so that neither the response nor its time tells whether the identifier
 * exists; the delivery names the identifier.
 */
final class CodeVerifier implements Verifier<CodeAnswer> {

  private static final int BOUND = 1_000_000;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final KeyService keys;
  private final AuthenticationOptions options;

  CodeVerifier(KeyService keys, AuthenticationOptions options) {
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
  public Future<Optional<Delivery>> prepare(ExchangeRecord exchange) {
    return exchange.claimant().recipient().compose(identifier -> {
      String code = String.format("%06d", RANDOM.nextInt(BOUND));
      return keys.sign(options.getMacKey(), data(exchange, code)).map(mac -> {
        if (identifier.isEmpty()) {
          return Optional.<Delivery> empty();
        }
        exchange.keep(this, mac.getSignature());
        return Optional.of(Delivery.newBuilder().setKind(identifier.get().getKind())
            .setIdentifier(identifier.get().getName()).setTo(identifier.get().getValue()).setCode(code).build());
      });
    });
  }

  @Override
  public Future<Optional<Evidence>> verify(ExchangeRecord exchange, CodeAnswer answer) {
    Optional<ByteString> mac = exchange.nonce().flatMap(nonce -> exchange.state(this));
    if (mac.isEmpty()) {
      return Future.succeededFuture(Optional.empty());
    }
    String code = answer.getCode().strip();
    return keys.verify(options.getMacKey(), data(exchange, code), mac.get().toByteArray(), "").map(verdict -> {
      if (!verdict.hasVerified()) {
        return Optional.empty();
      }
      exchange.forget(this);
      return Optional.of(Evidence.IDENTIFIER);
    });
  }

  private static byte[] data(ExchangeRecord exchange, String code) {
    return ("code\0" + exchange.id() + ":" + code).getBytes(StandardCharsets.UTF_8);
  }
}
