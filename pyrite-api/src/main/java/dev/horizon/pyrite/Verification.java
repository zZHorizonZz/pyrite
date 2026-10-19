package dev.horizon.pyrite;

import io.vertx.core.json.JsonObject;

/**
 * What {@link SigningKey#verify} found.
 */
public sealed interface Verification permits Verification.Verified, Verification.Rejected {

  /**
   * The signature holds, and so does everything that was expected of the token. {@code claims} is empty for a
   * signature over bytes.
   */
  record Verified(Version version, JsonObject claims) implements Verification {

    public Verified {
      claims = claims.copy();
    }

    @Override
    public JsonObject claims() {
      return claims.copy();
    }
  }

  record Rejected(Rejection reason, String description) implements Verification {
  }
}
