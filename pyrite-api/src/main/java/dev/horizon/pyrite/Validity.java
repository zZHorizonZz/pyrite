package dev.horizon.pyrite;

import java.time.Duration;
import java.util.Objects;

/**
 * How long each version of a key works, regardless of the rotation. A new version is {@link State.Pending} for
 * {@link #pending()}, then becomes primary. It expires when its {@link Limited#lifetime()} has passed since it was
 * created. Exported and uploaded versions expire in the same way.
 */
public sealed interface Validity permits Validity.Limited, Validity.Unlimited {

  /**
   * Returns a validity whose versions expire when the lifetime has passed since they were created.
   */
  static Validity of(Duration lifetime) {
    return new Limited(lifetime, Duration.ZERO);
  }

  /**
   * Returns a validity whose versions work until they are disabled or destroyed.
   */
  static Validity unlimited() {
    return new Unlimited(Duration.ZERO);
  }

  /**
   * Returns how long a new version is pending before it becomes primary. For a signing key, set it to the time for
   * which verifiers cache the JWK set.
   */
  Duration pending();

  /**
   * Returns a copy of this validity with the pending time.
   */
  Validity pending(Duration pending);

  record Limited(Duration lifetime, Duration pending) implements Validity {

    public Limited {
      Objects.requireNonNull(lifetime, "lifetime");
      Objects.requireNonNull(pending, "pending");
    }

    @Override
    public Validity pending(Duration pending) {
      return new Limited(lifetime, pending);
    }
  }

  record Unlimited(Duration pending) implements Validity {

    public Unlimited {
      Objects.requireNonNull(pending, "pending");
    }

    @Override
    public Validity pending(Duration pending) {
      return new Unlimited(pending);
    }
  }
}
