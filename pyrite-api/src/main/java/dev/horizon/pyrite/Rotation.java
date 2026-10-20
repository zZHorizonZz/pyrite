package dev.horizon.pyrite;

import java.time.Duration;
import java.util.Objects;

/**
 * When a key gets a new primary version. A rotation does not disable older versions. They stay live until they expire
 * according to the {@link Validity} of the key, or until they are disabled.
 */
public sealed interface Rotation permits Rotation.Every, Rotation.Never {

  /**
   * Returns a rotation that creates a new primary version after each period.
   */
  static Rotation every(Duration period) {
    return new Every(period);
  }

  /**
   * Returns a rotation that creates a new version only when {@link Key#rotate()} is called.
   */
  static Rotation never() {
    return new Never();
  }

  record Every(Duration period) implements Rotation {

    public Every {
      Objects.requireNonNull(period, "period");
    }
  }

  record Never() implements Rotation {
  }
}
