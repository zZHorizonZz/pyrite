package dev.horizon.pyrite;

import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Set;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * The options of {@link Pyrite}: the sizes of pages, the key of page tokens, and the options of its keys and of
 * authentication. Pyrite copies them when it starts, so a later change does not reach it.
 *
 * <pre>{@code
 * PyriteOptions options = new PyriteOptions().setMaxPageSize(500).setPageTokenKey(pageTokenKey)
 *     .setKeyOptions(new KeyOptions().setRoot(root));
 * }</pre>
 *
 * <p>
 * Give every Pyrite on the same database the same options. A key version opens only with the root key that sealed it,
 * and a refresh token works only with the MAC key that the options name.
 */
public class PyriteOptions {

  /** The page size of a list that names none: 50. */
  public static final int DEFAULT_DEFAULT_PAGE_SIZE = 50;
  /** The largest page: 1000. */
  public static final int DEFAULT_MAX_PAGE_SIZE = 1000;
  /** The most tuples that one read from OpenFGA takes: 100. */
  public static final int DEFAULT_TUPLES_PER_READ = 100;

  private static final int PAGE_TOKEN_KEY_BITS = 256;
  private static final Set<Integer> AES_KEY_BYTES = Set.of(16, 24, 32);

  private int defaultPageSize;
  private int maxPageSize;
  private int tuplesPerRead;
  private SecretKey pageTokenKey;
  private KeyOptions keyOptions;
  private AuthenticationOptions authenticationOptions;

  /**
   * Creates the default options: pages of 50 up to 1000, reads of 100 tuples, a random page token key, and the
   * default options of keys and of authentication, which name no root key, keys or relying party.
   */
  public PyriteOptions() {
    defaultPageSize = DEFAULT_DEFAULT_PAGE_SIZE;
    maxPageSize = DEFAULT_MAX_PAGE_SIZE;
    tuplesPerRead = DEFAULT_TUPLES_PER_READ;
    pageTokenKey = randomKey();
    keyOptions = new KeyOptions();
    authenticationOptions = new AuthenticationOptions();
  }

  /**
   * Creates a copy of options, with copies of their options of keys and of authentication.
   *
   * @param other the options to copy
   */
  public PyriteOptions(PyriteOptions other) {
    defaultPageSize = other.defaultPageSize;
    maxPageSize = other.maxPageSize;
    tuplesPerRead = other.tuplesPerRead;
    pageTokenKey = other.pageTokenKey;
    keyOptions = new KeyOptions(other.keyOptions);
    authenticationOptions = new AuthenticationOptions(other.authenticationOptions);
  }

  public int getDefaultPageSize() {
    return defaultPageSize;
  }

  /**
   * Sets the page size of a list that names none, such as {@code ListGrants} with a {@code page_size} of 0. The largest
   * page still bounds it.
   *
   * @param defaultPageSize the number of items, at least 1; 50 by default
   * @return these options
   * @throws IllegalArgumentException if the size is below 1
   */
  public PyriteOptions setDefaultPageSize(int defaultPageSize) {
    if (defaultPageSize < 1) {
      throw new IllegalArgumentException("The default page size is at least 1.");
    }
    this.defaultPageSize = defaultPageSize;
    return this;
  }

  public int getMaxPageSize() {
    return maxPageSize;
  }

  /**
   * Sets the largest page; a request for a larger one gets this size.
   *
   * @param maxPageSize the number of items, at least 1; 1000 by default
   * @return these options
   * @throws IllegalArgumentException if the size is below 1
   */
  public PyriteOptions setMaxPageSize(int maxPageSize) {
    if (maxPageSize < 1) {
      throw new IllegalArgumentException("The largest page is at least 1.");
    }
    this.maxPageSize = maxPageSize;
    return this;
  }

  public int getTuplesPerRead() {
    return tuplesPerRead;
  }

  /**
   * Sets the page size of Pyrite's reads of tuples from OpenFGA. It also bounds a page of {@code ListGrants} and of
   * {@code ListChanges}, which read one page of tuples each.
   *
   * @param tuplesPerRead the number of tuples, at least 1; 100 by default
   * @return these options
   * @throws IllegalArgumentException if the number is below 1
   */
  public PyriteOptions setTuplesPerRead(int tuplesPerRead) {
    if (tuplesPerRead < 1) {
      throw new IllegalArgumentException("A read takes at least 1 tuple.");
    }
    this.tuplesPerRead = tuplesPerRead;
    return this;
  }

  public SecretKey getPageTokenKey() {
    return pageTokenKey;
  }

  /**
   * Sets the AES key that the Cinder database of Pyrite's tables encrypts its page tokens with. The pages of
   * {@code ListGrants} and {@code ListChanges} carry the continuation tokens of OpenFGA instead.
   *
   * @param pageTokenKey the AES key of 128, 192 or 256 bits; a random key of 256 bits by default, which lasts as long
   * as these options
   * @return these options
   * @throws IllegalArgumentException if the key is not an AES key of 128, 192 or 256 bits, or does not give its bytes
   */
  public PyriteOptions setPageTokenKey(SecretKey pageTokenKey) {
    Objects.requireNonNull(pageTokenKey, "pageTokenKey");
    byte[] bytes = pageTokenKey.getEncoded();
    if (!"AES".equalsIgnoreCase(pageTokenKey.getAlgorithm()) || bytes == null
        || !AES_KEY_BYTES.contains(bytes.length)) {
      throw new IllegalArgumentException("The page token key is an AES key of 128, 192 or 256 bits.");
    }
    this.pageTokenKey = pageTokenKey;
    return this;
  }

  public KeyOptions getKeyOptions() {
    return keyOptions;
  }

  /**
   * Sets the options of keys. A declaration with {@code pyrite.dev/Key} needs their root key.
   *
   * @param keyOptions the options; by default, options without a root key
   * @return these options
   */
  public PyriteOptions setKeyOptions(KeyOptions keyOptions) {
    this.keyOptions = Objects.requireNonNull(keyOptions, "keyOptions");
    return this;
  }

  public AuthenticationOptions getAuthenticationOptions() {
    return authenticationOptions;
  }

  /**
   * Sets the options of accounts, sessions and exchanges. A declaration with {@code pyrite.dev/Account} needs their
   * keys, their issuer and their relying party.
   *
   * @param authenticationOptions the options; by default, options without keys, issuer or relying party
   * @return these options
   */
  public PyriteOptions setAuthenticationOptions(AuthenticationOptions authenticationOptions) {
    this.authenticationOptions = Objects.requireNonNull(authenticationOptions, "authenticationOptions");
    return this;
  }

  /**
   * Gives the page size of a request.
   *
   * @param requested the page size that the request names; 0 or less for none
   * @return the default page size for none, or the requested size, each up to the largest page
   */
  public int pageSize(int requested) {
    return requested <= 0 ? Math.min(defaultPageSize, maxPageSize) : Math.min(requested, maxPageSize);
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
}
