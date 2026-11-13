package dev.horizon.pyrite;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The options of accounts, sessions and exchanges. A declaration with {@code pyrite.dev/Account} needs the three keys,
 * the issuer, and the id, the name and at least one origin of the relying party.
 *
 * <pre>{@code
 * AuthenticationOptions authentication = new AuthenticationOptions().setMacKey("keys/sessions")
 *     .setEncryptionKey("keys/factors").setAccessTokenKey("keys/access").setIssuer("Acme")
 *     .setSessionLifetime(Duration.ofDays(14))
 *     .setRelyingPartyOptions(new RelyingPartyOptions().setId("acme.dev").setName("Acme")
 *         .addOrigin("https://acme.dev"));
 * }</pre>
 *
 * <p>
 * The three keys that the options name are resources of {@code pyrite.dev/Key}, which the application creates with
 * {@code CreateKey} after the first declaration: the MAC key with {@code HMAC_SHA256}, the encryption key with
 * {@code AES256_GCM}, and the access token key with {@code ES256}, {@code EDDSA} or {@code RS256}. The lifetimes of
 * sessions, exchanges and codes are counted by the clock of the database; the lifetime of an access token, which a
 * service checks on its own, by the clock of Java.
 */
public class AuthenticationOptions {

  /** The lifetime of an access token: 5 minutes. */
  public static final Duration DEFAULT_ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(5);
  /** The lifetime of a session: 30 days. */
  public static final Duration DEFAULT_SESSION_LIFETIME = Duration.ofDays(30);
  /** The lifetime of a session after its last use: 7 days. */
  public static final Duration DEFAULT_IDLE_LIFETIME = Duration.ofDays(7);
  /** The lifetime of an exchange: 15 minutes. */
  public static final Duration DEFAULT_EXCHANGE_LIFETIME = Duration.ofMinutes(15);
  /** The lifetime of a one-time code: 10 minutes. */
  public static final Duration DEFAULT_CODE_LIFETIME = Duration.ofMinutes(10);
  /** The most answers of an exchange: 5. */
  public static final int DEFAULT_MAX_ANSWERS = 5;

  private String macKey;
  private String encryptionKey;
  private String accessTokenKey;
  private Duration accessTokenLifetime;
  private RelyingPartyOptions relyingPartyOptions;
  private List<ProviderOptions> providers;
  private String issuer;
  private Duration sessionLifetime;
  private Duration idleLifetime;
  private Duration exchangeLifetime;
  private Duration codeLifetime;
  private int maxAnswers;
  private Argon2Options argon2Options;

  /**
   * Creates the default options: no keys, relying party or issuer; sessions of 30 days that idle out after 7, exchanges
   * of 15 minutes, codes of 10 minutes, 5 answers per exchange, and the default cost of password hashes.
   */
  public AuthenticationOptions() {
    relyingPartyOptions = new RelyingPartyOptions();
    providers = new ArrayList<>();
    accessTokenLifetime = DEFAULT_ACCESS_TOKEN_LIFETIME;
    sessionLifetime = DEFAULT_SESSION_LIFETIME;
    idleLifetime = DEFAULT_IDLE_LIFETIME;
    exchangeLifetime = DEFAULT_EXCHANGE_LIFETIME;
    codeLifetime = DEFAULT_CODE_LIFETIME;
    maxAnswers = DEFAULT_MAX_ANSWERS;
    argon2Options = new Argon2Options();
  }

  /**
   * Creates a copy of options, with copies of their relying party, their providers and their cost of password hashes.
   *
   * @param other the options to copy
   */
  public AuthenticationOptions(AuthenticationOptions other) {
    macKey = other.macKey;
    encryptionKey = other.encryptionKey;
    accessTokenKey = other.accessTokenKey;
    accessTokenLifetime = other.accessTokenLifetime;
    relyingPartyOptions = new RelyingPartyOptions(other.relyingPartyOptions);
    providers = new ArrayList<>(other.providers.stream().map(ProviderOptions::new).toList());
    issuer = other.issuer;
    sessionLifetime = other.sessionLifetime;
    idleLifetime = other.idleLifetime;
    exchangeLifetime = other.exchangeLifetime;
    codeLifetime = other.codeLifetime;
    maxAnswers = other.maxAnswers;
    argon2Options = new Argon2Options(other.argon2Options);
  }

  /**
   * Tells which of what a declaration with accounts needs these options lack.
   *
   * @return the names of the options that are not set, of {@code macKey}, {@code encryptionKey},
   * {@code accessTokenKey}, {@code issuer} and {@code relyingPartyOptions}, which lacks its id, its name or an origin;
   * empty when none is missing
   */
  public List<String> missing() {
    List<String> missing = new ArrayList<>();
    if (macKey == null) {
      missing.add("macKey");
    }
    if (encryptionKey == null) {
      missing.add("encryptionKey");
    }
    if (accessTokenKey == null) {
      missing.add("accessTokenKey");
    }
    if (issuer == null) {
      missing.add("issuer");
    }
    if (relyingPartyOptions.getId() == null || relyingPartyOptions.getName() == null
        || relyingPartyOptions.getOrigins().isEmpty()) {
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
   * Sets the name of the MAC key that signs refresh tokens, one-time codes and recovery codes.
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

  /**
   * Gives the name of the access token key.
   *
   * @return the name, or {@code null} when none is set
   */
  public String getAccessTokenKey() {
    return accessTokenKey;
  }

  /**
   * Sets the name of the key that signs access tokens. Services verify an access token with its public key, without a
   * call to Pyrite, so give the key no other use: a JWT that it signs for another purpose would pass as an access
   * token.
   *
   * @param accessTokenKey the name of a key of {@code ES256}, {@code EDDSA} or {@code RS256}, such as
   * {@code keys/access}; none by default
   * @return these options
   */
  public AuthenticationOptions setAccessTokenKey(String accessTokenKey) {
    this.accessTokenKey = Objects.requireNonNull(accessTokenKey, "accessTokenKey");
    return this;
  }

  public Duration getAccessTokenLifetime() {
    return accessTokenLifetime;
  }

  /**
   * Sets how long an access token lasts. A service that verifies it without a call to Pyrite takes it until then,
   * even after its session ends, so keep it short.
   *
   * @param accessTokenLifetime the lifetime, positive; 5 minutes by default
   * @return these options
   * @throws IllegalArgumentException if the lifetime is zero or negative
   */
  public AuthenticationOptions setAccessTokenLifetime(Duration accessTokenLifetime) {
    this.accessTokenLifetime = positive(accessTokenLifetime, "access token lifetime");
    return this;
  }

  /**
   * Gives the providers that people sign in at.
   *
   * @return copies of the providers, in the order that they were added; a change of them does not reach these options
   */
  public List<ProviderOptions> getProviders() {
    return providers.stream().map(ProviderOptions::new).toList();
  }

  /**
   * Sets the providers that people sign in at, in place of those before.
   *
   * @param providers the providers, each with a name, a Vert.x provider and a callback, and no two with the same name;
   * none by default
   * @return these options
   * @throws IllegalArgumentException if a provider lacks its name, its Vert.x provider or its callback, or has the name
   * of another
   */
  public AuthenticationOptions setProviders(List<ProviderOptions> providers) {
    List<ProviderOptions> copies = new ArrayList<>();
    for (ProviderOptions provider : providers) {
      add(copies, provider);
    }
    this.providers = copies;
    return this;
  }

  /**
   * Adds a provider that people sign in at, which an exchange takes as a factor, and whose accounts the stage
   * {@code enrol: PROVIDER} links. The options keep a copy of it, so a later change of the provider does not reach
   * them.
   *
   * @param provider the provider, with a name, a Vert.x provider and a callback
   * @return these options
   * @throws IllegalArgumentException if the provider lacks its name, its Vert.x provider or its callback, or has the
   * name of a provider that these options have
   */
  public AuthenticationOptions addProvider(ProviderOptions provider) {
    add(providers, provider);
    return this;
  }

  public RelyingPartyOptions getRelyingPartyOptions() {
    return relyingPartyOptions;
  }

  /**
   * Sets the WebAuthn relying party that passkeys are registered with.
   *
   * @param relyingPartyOptions the relying party, with its id, its name and its origins; by default one without them
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
   * Sets the issuer that authenticator apps show next to a TOTP code, which is also the {@code iss} of access
   * tokens.
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

  public Duration getExchangeLifetime() {
    return exchangeLifetime;
  }

  /**
   * Sets how long an exchange lasts after it starts, and how long an enrolment that no exchange finishes lasts before a
   * purge deletes it.
   *
   * @param exchangeLifetime the lifetime, positive; 15 minutes by default
   * @return these options
   * @throws IllegalArgumentException if the lifetime is zero or negative
   */
  public AuthenticationOptions setExchangeLifetime(Duration exchangeLifetime) {
    this.exchangeLifetime = positive(exchangeLifetime, "exchange lifetime");
    return this;
  }

  public Duration getCodeLifetime() {
    return codeLifetime;
  }

  /**
   * Sets how long the nonce of a stage lasts after the exchange reaches the stage: the one-time code that the stage
   * sends, and the challenge that a passkey answer of the stage signs.
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
   * Sets the most wrong answers that an exchange takes. After them, the exchange refuses even the right answer; a right
   * answer does not count.
   *
   * @param maxAnswers the number of wrong answers, at least 1; 5 by default
   * @return these options
   * @throws IllegalArgumentException if the number is below 1
   */
  public AuthenticationOptions setMaxAnswers(int maxAnswers) {
    if (maxAnswers < 1) {
      throw new IllegalArgumentException("An exchange takes at least one answer.");
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

  private static void add(List<ProviderOptions> providers, ProviderOptions provider) {
    Objects.requireNonNull(provider, "provider");
    if (provider.getName() == null || provider.getAuth() == null || provider.getRedirectUri() == null) {
      throw new IllegalArgumentException("A provider has a name, a Vert.x provider and a callback.");
    }
    if (providers.stream().anyMatch(other -> other.getName().equals(provider.getName()))) {
      throw new IllegalArgumentException("Two providers have the name " + provider.getName() + ".");
    }
    providers.add(new ProviderOptions(provider));
  }

  private static Duration positive(Duration lifetime, String name) {
    if (Objects.requireNonNull(lifetime, name).isNegative() || lifetime.isZero()) {
      throw new IllegalArgumentException("The " + name + " is positive.");
    }
    return lifetime;
  }
}
