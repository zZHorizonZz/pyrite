package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.PyriteException;
import io.vertx.grpc.common.GrpcStatus;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

record SessionToken(String id, String secret) {

  private static final int SECRET_BYTES = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  public static SessionToken parse(String token) {
    int dot = token.indexOf('.');
    if (dot <= 0 || dot == token.length() - 1) {
      throw new PyriteException(GrpcStatus.UNAUTHENTICATED, "The session token is not valid.");
    }
    return new SessionToken(token.substring(0, dot), token.substring(dot + 1));
  }

  static SessionToken generate() {
    byte[] secret = new byte[SECRET_BYTES];
    RANDOM.nextBytes(secret);
    return new SessionToken(UUID.randomUUID().toString(), Base64.getUrlEncoder().withoutPadding()
        .encodeToString(secret));
  }

  @Override
  public String toString() {
    return id + "." + secret;
  }
}
