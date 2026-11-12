package dev.horizon.pyrite;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The options of accounts, sessions and flows. A declaration with {@code pyrite.dev/Account} needs both keys, the
 * issuer, and the id and name of the relying party.
 *
 * <pre>{@code
 * AuthenticationOptions authentication = new AuthenticationOptions().setMacKey("keys/sessions")
 *     .setEncryptionKey("keys/factors").setIssuer("Acme").setSessionLifetime(Duration.ofDays(14))
 *     .setRelyingPartyOptions(new RelyingPartyOptions().setId("acme.dev").setName("Acme"));
 * }</pre>
 *
 * <p>
 * The two keys that the options name are resources of {@code pyrite.dev/Key}, which the application creates with
 * {@code CreateKey} after the first declaration: the MAC key with {@code HMAC_SHA256}, and the encryption key with
 * {@code AES256_GCM}. The lifetimes are counted by the clock of the database.
 */
public class AuthenticationOptions {

  /** The lifetime of a session: 30 days. */
  public static final Duration DEFAULT_SESSION_LIFETIME = Duration.ofDays(30);
  /** The lifetime of a session after its last use: 7 days. */
  public static final Duration DEFAULT_IDLE_LIFETIME = Duration.ofDays(7);
  /** The lifetime of a flow: 15 minutes. */
  public static final Duration DEFAULT_FLOW_LIFETIME = Duration.ofMinutes(15);
  /** The lifetime of a one-time code: 10 minutes. */
  public static final Duration DEFAULT_CODE_LIFETIME = Duration.ofMinutes(10);
  /** The most answers of a flow: 5. */
  public static final int DEFAULT_MAX_ANSWERS = 5;

  private String macKey;
  private String encryptionKey;
  private RelyingPartyOptions relyingPartyOptions;
  private String issuer;
  private Duration sessionLifetime;
  private Duration idleLifetime;
  private Duration flowLifetime;
  private Duration codeLifetime;
  private int maxAnswers;
  private Argon2Options argon2Options;

  /**
   * Creates the default options: no keys, relying party or issuer; sessions of 30 days that idle out after 7, flows
   * of 15 minutes, codes of 10 minutes, 5 answers per flow, and the default cost of password hashes.
   */
  public AuthenticationOptions() {
    relyingPartyOptions = new RelyingPartyOptions();
    sessionLifetime = DEFAULT_SESSION_LIFETIME;
    idleLifetime = DEFAULT_IDLE_LIFETIME;
    flowLifetime = DEFAULT_FLOW_LIFETIME;
    codeLifetime = DEFAULT_CODE_LIFETIME;
    maxAnswers = DEFAULT_MAX_ANSWERS;
    argon2Options = new Argon2Options();
  }

  /**
   * Creates a copy of options, with copies of their relying party and their cost of password hashes.
   *
   * @param other the options to copy
   */
  public AuthenticationOptions(AuthenticationOptions other) {
    macKey = other.macKey;
    encryptionKey = other.encryptionKey;
    relyingPartyOptions = new RelyingPartyOptions(other.relyingPartyOptions);
    issuer = other.issuer;
    sessionLifetime = other.sessionLifetime;
    idleLifetime = other.idleLifetime;
    flowLifetime = other.flowLifetime;
    codeLifetime = other.codeLifetime;
    maxAnswers = other.maxAnswers;
    argon2Options = new Argon2Options(other.argon2Options);
  }

  /**
   * Tells which of what a declaration with accounts needs these options lack.
   *
   * @return the names of the options that are not set, of {@code macKey}, {@code encryptionKey}, {@code issuer} and
   * {@code relyingPartyOptions}; empty when none is missing
   */
  public List<String> missing() {
    List<String> missing = new ArrayList<>();
    if (macKey == null) {
      missing.add("macKey");
    }
    if (encryptionKey == null) {
      missing.add("encryptionKey");
    }
    if (issuer == null) {
      missing.add("issuer");
    }
    if (relyingPartyOptions.getId() == null || relyingPartyOptions.getName() == null) {
      missing.add("relyingPartyOptions");
    }
    return missing;
  }

  /**
   * Gives the name of the MAC key.
   *
   * @return the name, or {@code null} when none is set
   */
  public String getMacKey() {
    return macKey;
  }

  /**
   * Sets the name of the MAC key that signs session tokens, one-time codes and recovery codes.
   *
   * @param macKey the name of a key of {@code HMAC_SHA256}, such as {@code keys/sessions}; none by default
   * @return these options
   */
  public AuthenticationOptions setMacKey(String macKey) {
    this.macKey = Objects.requireNonNull(macKey, "macKey");
    return this;
  }

  /**
   * Gives the name of the encryption key.
   *
   * @return the name, or {@code null} when none is set
   */
  public String getEncryptionKey() {
    return encryptionKey;
  }

  /**
   * Sets the name of the encryption key of the secrets of factors, such as the seed of a TOTP.
   *
   * @param encryptionKey the name of a key of {@code AES256_GCM}, such as {@code keys/factors}; none by default
   * @return these options
   */
  public AuthenticationOptions setEncryptionKey(String encryptionKey) {
    this.encryptionKey = Objects.requireNonNull(encryptionKey, "encryptionKey");
    return this;
  }

  public RelyingPartyOptions getRelyingPartyOptions() {
    return relyingPartyOptions;
  }

  /**
   * Sets the WebAuthn relying party that passkeys are registered with.
   *
   * @param relyingPartyOptions the relying party, with its id and name; by default one without them
   * @return these options
   */
  public AuthenticationOptions setRelyingPartyOptions(RelyingPartyOptions relyingPartyOptions) {
    this.relyingPartyOptions = Objects.requireNonNull(relyingPartyOptions, "relyingPartyOptions");
    return this;
  }

  /**
   * Gives the issuer.
   *
   * @return the issuer, or {@code null} when none is set
   */
  public String getIssuer() {
    return issuer;
  }

  /**
   * Sets the issuer that authenticator apps show next to a TOTP code.
   *
   * @param issuer the issuer, such as {@code Acme}; none by default
   * @return these options
   */
  public AuthenticationOptions setIssuer(String issuer) {
    this.issuer = Objects.requireNonNull(issuer, "issuer");
    return this;
  }

  public Duration getSessionLifetime() {
    return sessionLifetime;
  }

  /**
   * Sets how long a session lasts after it is issued, however often it is used.
   *
   * @param sessionLifetime the lifetime, positive; 30 days by default
   * @return these options
   * @throws IllegalArgumentException if the lifetime is zero or negative
   */
  public AuthenticationOptions setSessionLifetime(Duration sessionLifetime) {
    this.sessionLifetime = positive(sessionLifetime, "session lifetime");
    return this;
  }

  public Duration getIdleLifetime() {
    return idleLifetime;
  }

  /**
   * Sets how long a session lasts after its last authentication, up to its lifetime. Each authentication extends it,
   * at most once a minute.
   *
   * @param idleLifetime the lifetime, positive; 7 days by default
   * @return these options
   * @throws IllegalArgumentException if the lifetime is zero or negative
   */
  public AuthenticationOptions setIdleLifetime(Duration idleLifetime) {
    this.idleLifetime = positive(idleLifetime, "idle lifetime");
    return this;
  }

  public Duration getFlowLifetime() {
    return flowLifetime;
  }

  /**
   * Sets how long a flow lasts after it starts, and how long an enrolment that no flow finishes lasts before a purge
   * deletes it.
   *
   * @param flowLifetime the lifetime, positive; 15 minutes by default
   * @return these options
   * @throws IllegalArgumentException if the lifetime is zero or negative
   */
  public AuthenticationOptions setFlowLifetime(Duration flowLifetime) {
    this.flowLifetime = positive(flowLifetime, "flow lifetime");
    return this;
  }

  public Duration getCodeLifetime() {
    return codeLifetime;
  }

  /**
   * Sets how long a one-time code lasts after Pyrite makes it.
   *
   * @param codeLifetime the lifetime, positive; 10 minutes by default
   * @return these options
   * @throws IllegalArgumentException if the lifetime is zero or negative
   */
  public AuthenticationOptions setCodeLifetime(Duration codeLifetime) {
    this.codeLifetime = positive(codeLifetime, "code lifetime");
    return this;
  }

  public int getMaxAnswers() {
    return maxAnswers;
  }

  /**
   * Sets the most wrong answers that a flow takes. After them, the flow refuses even the right answer; a right answer
   * does not count.
   *
   * @param maxAnswers the number of wrong answers, at least 1; 5 by default
   * @return these options
   * @throws IllegalArgumentException if the number is below 1
   */
  public AuthenticationOptions setMaxAnswers(int maxAnswers) {
    if (maxAnswers < 1) {
      throw new IllegalArgumentException("A flow takes at least one answer.");
    }
    this.maxAnswers = maxAnswers;
    return this;
  }

  public Argon2Options getArgon2Options() {
    return argon2Options;
  }

  /**
   * Sets the cost of the hashes of passwords.
   *
   * @param argon2Options the cost; by default the second recommended option of RFC 9106
   * @return these options
   */
  public AuthenticationOptions setArgon2Options(Argon2Options argon2Options) {
    this.argon2Options = Objects.requireNonNull(argon2Options, "argon2Options");
    return this;
  }

  private static Duration positive(Duration lifetime, String name) {
    if (lifetime.isNegative() || lifetime.isZero()) {
      throw new IllegalArgumentException("The " + name + " is positive.");
    }
    return lifetime;
  }
}
