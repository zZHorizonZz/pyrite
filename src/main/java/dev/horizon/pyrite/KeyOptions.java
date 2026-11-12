package dev.horizon.pyrite;

import com.google.crypto.tink.Aead;
import java.time.Duration;
import java.util.Objects;

/**
 * The options of Pyrite's keys: the root key that seals the material of every key version, and how far the clock of
 * another server may be off when a JWT that it made is verified.
 *
 * <pre>{@code
 * KeyOptions keys = new KeyOptions().setRoot(root).setClockSkew(Duration.ofSeconds(30));
 * }</pre>
 */
public class KeyOptions {

  /** The clock skew of a verification that names no leeway: 5 seconds. */
  public static final Duration DEFAULT_CLOCK_SKEW = Duration.ofSeconds(5);

  private Aead root;
  private Duration clockSkew;

  /**
   * Creates the default options: no root key, and a clock skew of 5 seconds.
   */
  public KeyOptions() {
    clockSkew = DEFAULT_CLOCK_SKEW;
  }

  /**
   * Creates a copy of options, with the same root key.
   *
   * @param other the options to copy
   */
  public KeyOptions(KeyOptions other) {
    root = other.root;
    clockSkew = other.clockSkew;
  }

  /**
   * Gives the root key.
   *
   * @return the root key, or {@code null} when none is set
   */
  public Aead getRoot() {
    return root;
  }

  /**
   * Sets the root key, which seals the material of every key version. Keep it outside the database, such as in a
   * cloud KMS, and give every Pyrite on the database the same one: a version opens only with the root key that sealed
   * it.
   *
   * @param root the Tink AEAD of the root key; none by default, and a declaration with {@code pyrite.dev/Key} needs one
   * @return these options
   */
  public KeyOptions setRoot(Aead root) {
    this.root = Objects.requireNonNull(root, "root");
    return this;
  }

  public Duration getClockSkew() {
    return clockSkew;
  }

  /**
   * Sets how far the {@code exp} and {@code nbf} of a JWT may be off, when a verification names no leeway.
   *
   * @param clockSkew the skew, zero or more; 5 seconds by default
   * @return these options
   * @throws IllegalArgumentException if the skew is negative
   */
  public KeyOptions setClockSkew(Duration clockSkew) {
    if (clockSkew.isNegative()) {
      throw new IllegalArgumentException("The clock skew is not negative.");
    }
    this.clockSkew = clockSkew;
    return this;
  }
}
