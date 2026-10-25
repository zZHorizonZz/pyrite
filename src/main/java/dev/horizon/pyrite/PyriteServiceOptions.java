package dev.horizon.pyrite;

import com.google.crypto.tink.Aead;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * The limits, the keys and the clock of Pyrite's services. {@code new PyriteServiceOptions()} has the default limits,
 * a random page token key, no root key, no options of authentication, and the system clock. Each {@code with} method
 * gives a copy with one value changed.
 *
 * <p>
 * Give every service of Pyrite on the same database the same options. A page token works only at a service with the
 * key that made it, and a key version only with the root key that encrypted it.
 *
 * @param defaultPageSize the page size of a list that names none
 * @param maxPageSize the largest page; a request for a larger one gets this size
 * @param tuplesPerRead the page size of Pyrite's reads of tuples from OpenFGA
 * @param pageTokenKey the AES key that encrypts page tokens
 * @param root the Tink {@link Aead} that encrypts the material of key versions; a declaration with
 * {@code pyrite.dev/Key} needs one
 * @param authentication the options of accounts, sessions and flows; a declaration with {@code pyrite.dev/Account}
 * needs them
 * @param clock the clock of every time that Pyrite stores and compares
 */
public record PyriteServiceOptions(int defaultPageSize, int maxPageSize, int tuplesPerRead,
    SecretKey pageTokenKey, Optional<Aead> root, Optional<Authentication> authentication, Clock clock) {

  private static final int DEFAULT_PAGE_SIZE = 50;
  private static final int MAX_PAGE_SIZE = 1000;
  private static final int TUPLES_PER_READ = 100;
  private static final int PAGE_TOKEN_KEY_BITS = 256;

  public PyriteServiceOptions {
    if (defaultPageSize < 1 || maxPageSize < defaultPageSize || tuplesPerRead < 1) {
      throw new IllegalArgumentException("Every limit is at least 1, and the largest page is at least the default.");
    }
    Objects.requireNonNull(pageTokenKey, "pageTokenKey");
    Objects.requireNonNull(root, "root");
    Objects.requireNonNull(authentication, "authentication");
    Objects.requireNonNull(clock, "clock");
  }

  /**
   * Creates the default options: a page size of 50, a largest page of 1000, reads of 100 tuples, a
   * random page token key, no root key, no options of authentication, and the system clock in UTC.
   */
  public PyriteServiceOptions() {
    this(DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE, TUPLES_PER_READ, randomKey(), Optional.empty(),
        Optional.empty(), Clock.systemUTC());
  }

  /**
   * Gives the options with a root key, which encrypts the material of every key version. Keep it outside the
   * database, such as in a cloud KMS.
   *
   * @param root the root key
   * @return the options, with the other values unchanged
   */
  public PyriteServiceOptions withRoot(Aead root) {
    return new PyriteServiceOptions(defaultPageSize, maxPageSize, tuplesPerRead, pageTokenKey,
        Optional.of(root), authentication, clock);
  }

  /**
   * Gives the options with the options of authentication, which the authentication service and accounts need.
   *
   * @param authentication the options of accounts, sessions and flows
   * @return the options, with the other values unchanged
   */
  public PyriteServiceOptions withAuthentication(Authentication authentication) {
    return new PyriteServiceOptions(defaultPageSize, maxPageSize, tuplesPerRead, pageTokenKey, root,
        Optional.of(authentication), clock);
  }

  /**
   * Gives the options with another clock, such as a fixed clock in tests.
   *
   * @param clock the clock
   * @return the options, with the other values unchanged
   */
  public PyriteServiceOptions withClock(Clock clock) {
    return new PyriteServiceOptions(defaultPageSize, maxPageSize, tuplesPerRead, pageTokenKey, root,
        authentication, clock);
  }

  /**
   * Gives the page size of a request.
   *
   * @param requested the page size that the request names; 0 or less for none
   * @return the default page size for none, or the requested size up to the largest page
   */
  public int pageSize(int requested) {
    return requested <= 0 ? defaultPageSize : Math.min(requested, maxPageSize);
  }

  private static SecretKey randomKey() {
    try {
      KeyGenerator generator = KeyGenerator.getInstance("AES");
      generator.init(PAGE_TOKEN_KEY_BITS);
      return generator.generateKey();
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("Every Java platform has AES", impossible);
    }
  }

  /**
   * The options of accounts, sessions and flows. The two keys that it names are resources of {@code pyrite.dev/Key}:
   * create them after the first declaration, with the purposes {@code MAC} and {@code ENCRYPTION}.
   *
   * @param macKey the name of the MAC key that signs session tokens and codes, such as {@code keys/sessions}
   * @param encryptionKey the name of the encryption key of factor secrets, such as {@code keys/factors}
   * @param relyingParty the WebAuthn relying party of passkeys
   * @param issuer the issuer that authenticator apps show next to a TOTP code, such as {@code Acme}
   * @param sessionLifetime how long a session lasts after it is issued
   * @param idleLifetime how long a session lasts after its last authentication, up to its lifetime
   * @param flowLifetime how long a flow lasts after it starts
   * @param codeLifetime how long a code lasts after Pyrite generates it
   * @param maxAnswers the most answers that a flow takes, wrong or right
   * @param argon2 the cost of password hashes
   */
  public record Authentication(String macKey, String encryptionKey, RelyingParty relyingParty, String issuer,
      Duration sessionLifetime, Duration idleLifetime, Duration flowLifetime, Duration codeLifetime, int maxAnswers,
      Argon2 argon2) {

    private static final Duration SESSION_LIFETIME = Duration.ofDays(30);
    private static final Duration IDLE_LIFETIME = Duration.ofDays(7);
    private static final Duration FLOW_LIFETIME = Duration.ofMinutes(15);
    private static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    private static final int MAX_ANSWERS = 5;

    public Authentication {
      Objects.requireNonNull(macKey, "macKey");
      Objects.requireNonNull(encryptionKey, "encryptionKey");
      Objects.requireNonNull(relyingParty, "relyingParty");
      Objects.requireNonNull(issuer, "issuer");
      Objects.requireNonNull(argon2, "argon2");
      for (Duration lifetime : List.of(sessionLifetime, idleLifetime, flowLifetime, codeLifetime)) {
        if (lifetime.isNegative() || lifetime.isZero()) {
          throw new IllegalArgumentException("Every lifetime is positive.");
        }
      }
      if (maxAnswers < 1) {
        throw new IllegalArgumentException("A flow takes at least one answer.");
      }
    }

    /**
     * Creates the options of authentication with the default lifetimes and limits: sessions of 30 days that idle out
     * after 7, flows of 15 minutes, codes of 10 minutes, 5 answers per flow, and {@link Argon2#RFC_9106}.
     *
     * @param macKey the name of the MAC key that signs session tokens and codes, such as {@code keys/sessions}
     * @param encryptionKey the name of the encryption key of factor secrets, such as {@code keys/factors}
     * @param relyingParty the WebAuthn relying party of passkeys
     * @param issuer the issuer that authenticator apps show next to a TOTP code, such as {@code Acme}
     */
    public Authentication(String macKey, String encryptionKey, RelyingParty relyingParty, String issuer) {
      this(macKey, encryptionKey, relyingParty, issuer, SESSION_LIFETIME, IDLE_LIFETIME, FLOW_LIFETIME,
          CODE_LIFETIME, MAX_ANSWERS, Argon2.RFC_9106);
    }
  }

  /**
   * The WebAuthn relying party that passkeys are registered with.
   *
   * @param id the relying party id, the application's domain, such as {@code acme.dev}
   * @param name the name that authenticators show, such as {@code Acme}
   * @param origins the origins that passkey answers may come from, such as {@code https://acme.dev}
   */
  public record RelyingParty(String id, String name, List<String> origins) {

    public RelyingParty {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(name, "name");
      origins = List.copyOf(origins);
    }
  }

  /**
   * The cost of Argon2id password hashes.
   *
   * @param memory the memory, in KiB
   * @param iterations the passes over the memory
   * @param parallelism the lanes
   */
  public record Argon2(int memory, int iterations, int parallelism) {

    /**
     * The second recommended option of RFC 9106: 64 MiB of memory, 3 passes and 4 lanes.
     */
    public static final Argon2 RFC_9106 = new Argon2(65_536, 3, 4);
  }
}
