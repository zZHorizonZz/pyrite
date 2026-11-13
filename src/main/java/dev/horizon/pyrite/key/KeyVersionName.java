package dev.horizon.pyrite.key;

import dev.horizon.pyrite.schema.keys.v1.Tables;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The name of a key version, {@code keyVersions/{key}-{number}}: the id of its key, and its number among the versions
 * of the key. Its id, {@code {key}-{number}}, is what a ciphertext and the key id of a JWT name.
 *
 * @param key the id of the key, such as {@code payments}
 * @param number the number, from 1
 */
public record KeyVersionName(String key, long number) {

  static final String PREFIX = "keyVersions/";

  private static final Pattern ID = Pattern.compile("(.+)-([1-9][0-9]{0,17})");

  /**
   * Reads the name of a version.
   *
   * @param name the name, such as {@code keyVersions/payments-2}
   * @return the name, or empty for text that is not the name of a version
   */
  public static Optional<KeyVersionName> parse(String name) {
    return name.startsWith(PREFIX) ? fromId(name.substring(PREFIX.length())) : Optional.empty();
  }

  /**
   * Reads the id of a version.
   *
   * @param id the id, such as {@code payments-2}
   * @return the name, or empty for text that is not the id of a version
   */
  public static Optional<KeyVersionName> fromId(String id) {
    Matcher matcher = ID.matcher(id);
    return matcher.matches() ? Optional.of(new KeyVersionName(matcher.group(1), Long.parseLong(matcher.group(2))))
        : Optional.empty();
  }

  /**
   * Gives the name of a version's row.
   *
   * @throws IllegalStateException for a row whose name is not a version's
   */
  static KeyVersionName of(Tables.KeyVersion version) {
    return parse(version.getName()).orElseThrow(() -> new IllegalStateException("The row of a key version has the"
        + " name " + version.getName() + ", which is not a version's"));
  }

  /**
   * Gives the id of the version, which a ciphertext and the key id of a JWT name.
   *
   * @return the id, such as {@code payments-2}
   */
  public String id() {
    return key + "-" + number;
  }

  /**
   * Gives the name of the version.
   *
   * @return the name, such as {@code keyVersions/payments-2}
   */
  public String name() {
    return PREFIX + id();
  }

  @Override
  public String toString() {
    return name();
  }
}
