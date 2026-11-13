package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.Exchange;
import io.vertx.core.json.JsonObject;
import java.util.Base64;

/**
 * The WebAuthn options that an exchange gives with its challenge of a passkey.
 */
final class PasskeyOptions {

  static final String METHOD = "pyrite.authentication.v1.PasskeyAnswer";

  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

  private PasskeyOptions() {
  }

  static JsonObject of(Exchange exchange) throws Exception {
    for (Challenge challenge : exchange.getChallengesList()) {
      if (challenge.getMethod().equals(METHOD)) {
        return new JsonObject(challenge.getParameters().unpack(
            dev.horizon.pyrite.authentication.v1.PasskeyOptions.class).getOptions());
      }
    }
    throw new AssertionError("The exchange " + exchange.getName() + " takes no passkey now");
  }

  static byte[] decode(String text) {
    return DECODER.decode(text);
  }

  static String encode(byte[] bytes) {
    return ENCODER.encodeToString(bytes);
  }
}
