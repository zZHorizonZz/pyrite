package dev.horizon.pyrite;

import java.time.Instant;

/**
 * Where a version is in its life.
 */
public sealed interface State permits State.Pending, State.Primary, State.Live, State.Disabled, State.Destroyed {

  /**
   * @return whether the version decrypts, matches and verifies
   */
  boolean live();

  /**
   * A signing version that is published and becomes primary at {@code primaryAt}.
   */
  record Pending(Instant primaryAt) implements State {

    @Override
    public boolean live() {
      return true;
    }
  }

  /**
   * The version that encrypts, fingerprints or signs, since {@code since}.
   */
  record Primary(Instant since) implements State {

    @Override
    public boolean live() {
      return true;
    }
  }

  /**
   * A version that is no longer primary, or an exported or uploaded one, which never is. A retired signing version
   * stays published until {@code publishedUntil}; for every other version it is {@link Instant#MAX}.
   */
  record Live(Instant publishedUntil) implements State {

    @Override
    public boolean live() {
      return true;
    }
  }

  record Disabled(Instant at) implements State {

    @Override
    public boolean live() {
      return false;
    }
  }

  record Destroyed(Instant at) implements State {

    @Override
    public boolean live() {
      return false;
    }
  }
}
