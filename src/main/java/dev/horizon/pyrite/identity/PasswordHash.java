package dev.horizon.pyrite.identity;

import dev.horizon.pyrite.Argon2Options;
import dev.horizon.pyrite.PyriteException;
import io.vertx.grpc.common.GrpcStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/**
 * The Argon2id hash of a password, in the PHC string form, such as {@code $argon2id$v=19$m=65536,t=3,p=4$salt$hash},
 * with a random salt of 16 bytes and a hash of 32. A hash holds its parameters, so a hash made under older
 * {@link Argon2Options} still matches.
 *
 * @param encoded the PHC string
 */
record PasswordHash(String encoded) {

  private static final int MIN = 8;
  private static final int MAX = 128;
  private static final int SALT = 16;
  private static final int HASH = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  /**
   * Hashes a password with a new random salt. It blocks for as long as the options make Argon2id take, so call it off
   * the event loop.
   *
   * @param password the password, of 8 to 128 code points
   * @param argon2 the memory, iterations and parallelism of Argon2id
   * @return the hash
   * @throws PyriteException with {@code INVALID_ARGUMENT} for a password of another length
   */
  public static PasswordHash of(String password, Argon2Options argon2) {
    int length = password.codePointCount(0, password.length());
    if (length < MIN || length > MAX) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "A password has " + MIN + " to " + MAX
          + " characters, as NIST SP 800-63B requires.");
    }
    byte[] salt = new byte[SALT];
    RANDOM.nextBytes(salt);
    byte[] hash = derive(password, salt, argon2.getMemory(), argon2.getIterations(), argon2.getParallelism());
    Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
    return new PasswordHash("$argon2id$v=19$m=" + argon2.getMemory() + ",t=" + argon2.getIterations() + ",p="
        + argon2.getParallelism() + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(hash));
  }

  /**
   * Tells whether a password is the one of this hash. It hashes the password with the salt and the cost that the hash
   * names, so a hash made at another cost still matches, and compares in constant time.
   *
   * @param password the password to check
   * @return whether it matches; false for a hash that is not Argon2id
   */
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
