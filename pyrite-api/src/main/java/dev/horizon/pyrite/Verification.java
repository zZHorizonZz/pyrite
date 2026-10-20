package dev.horizon.pyrite;

import io.vertx.core.json.JsonObject;

/**
 * The result of {@link SigningKey#verify}.
 */
public sealed interface Verification permits Verification.Verified, Verification.Rejected {

  /**
   * The signature is valid, and the JWT meets every expectation. {@code claims} is empty for a signature over data.
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
