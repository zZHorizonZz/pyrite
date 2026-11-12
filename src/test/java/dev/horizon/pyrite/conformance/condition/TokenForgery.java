package dev.horizon.pyrite.conformance.condition;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * A forgery of a JWT of the key {@code keys/oauth}, made from a token that a key signed.
 */
public enum TokenForgery {
  /**
   * The claims of user:root, with the header and the signature of the token.
   */
  PAYLOAD,
  /**
   * The signature reversed.
   */
  REVERSED_SIGNATURE,
  /**
   * An empty signature.
   */
  EMPTY_SIGNATURE,
  /**
   * No signature part at all.
   */
  NO_SIGNATURE,
  /**
   * A header of the algorithm none, with no signature.
   */
  ALGORITHM_NONE,
  /**
   * A header that marks a parameter critical.
   */
  CRITICAL_HEADER,
  /**
   * A header with no key id.
   */
  NO_KEY_ID,
  /**
   * A header with the key id of a version that the key does not have.
   */
  UNKNOWN_KEY_ID,
  /**
   * A header with the key id of the first version of keys/oauth.
   */
  OAUTH_KEY_ID;

  private static final long EXPIRES_IN = 600;

  String apply(String token) {
    String[] parts = token.split("\\.");
    return switch (this) {
      case PAYLOAD -> parts[0] + "." + encoded("{\"iss\":\"" + SignToken.ISSUER + "\",\"aud\":\""
          + SignToken.AUDIENCE + "\",\"sub\":\"user:root\",\"exp\":" + (Instant.now().getEpochSecond()
              + EXPIRES_IN)
          + "}") + "." + parts[2];
      case REVERSED_SIGNATURE -> parts[0] + "." + parts[1] + "." + new StringBuilder(parts[2]).reverse();
      case EMPTY_SIGNATURE -> parts[0] + "." + parts[1] + ".";
      case NO_SIGNATURE -> parts[0] + "." + parts[1];
      case ALGORITHM_NONE -> encoded("{\"alg\":\"none\",\"typ\":\"at+jwt\",\"kid\":\"oauth-1\"}") + "."
          + parts[1] + ".";
      case CRITICAL_HEADER -> encoded("{\"kid\":\"oauth-1\",\"typ\":\"at+jwt\",\"alg\":\"ES256\","
          + "\"crit\":[\"exp\"],\"exp\":1}") + "." + parts[1] + "." + parts[2];
      case NO_KEY_ID -> encoded("{\"typ\":\"at+jwt\",\"alg\":\"ES256\"}") + "." + parts[1] + "." + parts[2];
      case UNKNOWN_KEY_ID -> encoded("{\"kid\":\"oauth-9\",\"typ\":\"at+jwt\",\"alg\":\"ES256\"}") + "."
          + parts[1] + "." + parts[2];
      case OAUTH_KEY_ID -> encoded("{\"kid\":\"oauth-1\",\"typ\":\"at+jwt\",\"alg\":\"ES256\"}") + "."
          + parts[1] + "." + parts[2];
    };
  }

  private static String encoded(String json) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }
}
