package dev.horizon.pyrite;

import java.time.Duration;

/**
 * When a key gets a new primary version. Rotating never destroys a version: an encryption or MAC key's older versions
 * stay live until they are disabled or destroyed, and a signing key's stay published for {@link Signing#kept()}.
 */
public sealed interface Rotation permits Rotation.Every, Rotation.Never {

  /**
   * @return a rotation that makes a new primary version every {@code period}
   */
  static Rotation every(Duration period) {
    return new Every(period);
  }

  /**
   * @return a rotation that makes new versions only when {@link Key#rotate()} is called
   */
  static Rotation never() {
    return new Never();
  }

  record Every(Duration period) implements Rotation {

    public Every {
      if (period.isNegative() || period.isZero()) {
        throw new IllegalArgumentException("A rotation period is positive: " + period);
      }
    }
  }

  record Never() implements Rotation {
  }
}
