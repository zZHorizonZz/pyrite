package dev.horizon.pyrite;

import java.time.Instant;

/**
 * The state of a version.
 */
public sealed interface State
    permits State.Pending, State.Primary, State.Live, State.Disabled, State.Expired, State.Destroyed {

  /**
   * Returns whether the version decrypts, checks MACs and verifies. A signing key publishes its live versions.
   */
  boolean live();

  /**
   * A new version that becomes primary at {@code primaryAt}. It is already live.
   */
  record Pending(Instant primaryAt) implements State {

    @Override
    public boolean live() {
      return true;
    }
  }

  /**
   * The version that encrypts, computes MACs or signs, since {@code since}.
   */
  record Primary(Instant since) implements State {

    @Override
    public boolean live() {
      return true;
    }
  }

  /**
   * A former primary version, or an exported or uploaded version.
   */
  record Live() implements State {

    @Override
    public boolean live() {
      return true;
    }
  }

  /**
   * A version that was disabled at {@code at}. It can be enabled again.
   */
  record Disabled(Instant at) implements State {

    @Override
    public boolean live() {
      return false;
    }
  }

  /**
   * A version whose lifetime ended at {@code at}. It cannot be enabled again.
   */
  record Expired(Instant at) implements State {

    @Override
    public boolean live() {
      return false;
    }
  }

  /**
   * A version whose material was deleted at {@code at}.
   */
  record Destroyed(Instant at) implements State {

    @Override
    public boolean live() {
      return false;
    }
  }
}
