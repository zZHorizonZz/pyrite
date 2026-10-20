package dev.horizon.pyrite.internal;

import dev.horizon.pyrite.Encryption;
import dev.horizon.pyrite.Mac;
import dev.horizon.pyrite.Purpose;
import dev.horizon.pyrite.Signing;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.security.SecureRandom;

public final class Generator {

  private final Vertx vertx;
  private final SecureRandom random = new SecureRandom();

  public Generator(Vertx vertx) {
    this.vertx = vertx;
  }

  public Future<Material> material(Purpose<?> purpose) {
    return switch (purpose) {
      case Encryption encryption -> {
        byte[] key = new byte[AesMaterial.KEY_BYTES];
        random.nextBytes(key);
        yield Future.succeededFuture(new AesMaterial(key));
      }
      case Mac mac -> {
        byte[] key = new byte[HmacMaterial.KEY_BYTES];
        random.nextBytes(key);
        yield Future.succeededFuture(new HmacMaterial(key));
      }
      case Signing signing -> {
        Scheme scheme = Scheme.valueOf(signing.algorithm().name());
        yield vertx.executeBlocking(() -> new KeyPairMaterial(scheme, scheme.generated()));
      }
    };
  }

}
