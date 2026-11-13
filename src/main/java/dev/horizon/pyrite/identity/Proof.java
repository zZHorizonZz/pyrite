package dev.horizon.pyrite.identity;

/**
 * What a right answer proves of the person who gave it: one of the factors. Two answers that prove the same
 * factor count as one.
 */
public enum Proof {
  /** Something the person knows, such as a password. */
  KNOWLEDGE,
  /** Something the person has, such as an authenticator app, a recovery code or a passkey. */
  POSSESSION,
  /** Something the person is, which an authenticator checks before it signs, such as by a fingerprint. */
  INHERENCE,
  /** A sign-in at a provider, such as Google, which counts as a factor of its own. */
  FEDERATION
}
