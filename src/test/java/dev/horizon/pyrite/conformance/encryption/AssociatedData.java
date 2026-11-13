package dev.horizon.pyrite.conformance.encryption;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.Decrypt;
import dev.horizon.pyrite.conformance.condition.Encrypt;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;

@Summary("A ciphertext decrypts only with its own associated data.")
final class AssociatedData extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    for (List<String> pair : List.of(List.of("orders/1042", "orders/1043"), List.of("orders/1042", ""),
        List.of("", "orders/1042"), List.of("orders/1042", "ORDERS/1042"))) {
      call(new Encrypt(ANNE, "keys/payments", "card", pair.get(0)));
      callAndContinueOnFailure(
          new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new Decrypt(ANNE, "keys/payments", Value.CIPHERTEXT,
              pair.get(1))),
          Requirement.KEY_TAMPERING);
    }
  }
}
