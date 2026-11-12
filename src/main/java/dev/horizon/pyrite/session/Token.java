package dev.horizon.pyrite.session;

import dev.horizon.pyrite.PyriteException;
import io.vertx.grpc.common.GrpcStatus;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * A session token: the id of its session, a dot, and a secret of 32 random bytes in base64url without padding, such
 * as {@code 3d5e7a1c-0f42-4b8e-9c61-2a7d9e4f1b03.q8C3...}. The id finds the session, and the secret proves the token;
 * the session keeps only a MAC of the secret.
 *
 * @param id the id of the session, the last segment of its name
 * @param secret the secret
 */
public record Token(String id, String secret) {

  private static final int SECRET_BYTES = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  /**
   * Reads a token that a client gives.
   *
   * @param token the token, as {@link #toString()} gave it
   * @return the token
   * @throws PyriteException with {@code UNAUTHENTICATED} when the text has no id or no secret around a dot
   */
  public static Token parse(String token) {
    int dot = token.indexOf('.');
    if (dot <= 0 || dot == token.length() - 1) {
      throw new PyriteException(GrpcStatus.UNAUTHENTICATED, "The session token is not valid.");
    }
    return new Token(token.substring(0, dot), token.substring(dot + 1));
  }

  /**
   * Makes a token of a new session, with a random id and a random secret.
   *
   * @return the token
   */
  public static Token generate() {
    byte[] secret = new byte[SECRET_BYTES];
    RANDOM.nextBytes(secret);
    return new Token(UUID.randomUUID().toString(), Base64.getUrlEncoder().withoutPadding()
        .encodeToString(secret));
  }

  /**
   * Gives the token as a client holds it.
   *
   * @return the id, a dot, and the secret
   */
  @Override
  public String toString() {
    return id + "." + secret;
  }
}
