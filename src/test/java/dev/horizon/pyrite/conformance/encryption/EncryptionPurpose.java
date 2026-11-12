package dev.horizon.pyrite.conformance.encryption;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateKey;
import dev.horizon.pyrite.conformance.condition.Encrypt;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.crypto.v1.Algorithm;
import io.vertx.grpc.common.GrpcStatus;

@Summary("Only an encryption key encrypts.")
final class EncryptionPurpose extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    call(new CreateKey("organizations/acme", "codes", Algorithm.HMAC_SHA256));
    call(new CreateKey("organizations/acme", "oauth", Algorithm.ES256));
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.FAILED_PRECONDITION, new Encrypt(ANNE, "keys/codes", "x", "")),
        Requirement.KEY_PURPOSE);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.FAILED_PRECONDITION, new Encrypt(ANNE, "keys/oauth", "x", "")),
        Requirement.KEY_PURPOSE);
  }
}
