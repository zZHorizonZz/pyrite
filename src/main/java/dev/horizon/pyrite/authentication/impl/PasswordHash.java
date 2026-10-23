package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.PyriteServiceOptions;
import io.vertx.grpc.common.GrpcStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

record PasswordHash(String encoded) {

  private static final int MIN = 8;
  private static final int MAX = 128;
  private static final int SALT = 16;
  private static final int HASH = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  public static PasswordHash of(String password, PyriteServiceOptions.Argon2 argon2) {
    int length = password.codePointCount(0, password.length());
    if (length < MIN || length > MAX) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "A password has " + MIN + " to " + MAX
          + " characters, as NIST SP 800-63B requires.");
    }
    byte[] salt = new byte[SALT];
    RANDOM.nextBytes(salt);
    byte[] hash = derive(password, salt, argon2.memory(), argon2.iterations(), argon2.parallelism());
    Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
    return new PasswordHash("$argon2id$v=19$m=" + argon2.memory() + ",t=" + argon2.iterations() + ",p="
        + argon2.parallelism() + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(hash));
  }

  public boolean matches(String password) {
    String[] parts = encoded.split("\\$");
    if (parts.length != 6 || !parts[1].equals("argon2id")) {
      return false;
    }
    int memory = 0;
    int iterations = 0;
    int parallelism = 0;
    for (String parameter : parts[3].split(",")) {
      int value = Integer.parseInt(parameter.substring(2));
      switch (parameter.charAt(0)) {
        case 'm' -> memory = value;
        case 't' -> iterations = value;
        case 'p' -> parallelism = value;
        default -> {
          return false;
        }
      }
    }
    byte[] expected = Base64.getDecoder().decode(parts[5]);
    byte[] actual = derive(password, Base64.getDecoder().decode(parts[4]), memory, iterations, parallelism);
    return MessageDigest.isEqual(expected, actual);
  }

  private static byte[] derive(String password, byte[] salt, int memory, int iterations, int parallelism) {
    Argon2BytesGenerator generator = new Argon2BytesGenerator();
    generator.init(new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
        .withMemoryAsKB(memory)
        .withIterations(iterations)
        .withParallelism(parallelism)
        .withSalt(salt)
        .build());
    byte[] hash = new byte[HASH];
    generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), hash);
    return hash;
  }
}
