package dev.horizon.pyrite.declaration;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import dev.horizon.pyrite.Acme;
import dev.horizon.pyrite.KeyOptions;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.PyriteOptions;
import dev.horizon.pyrite.PyriteTest;
import dev.horizon.pyrite.deployment.Deployment;
import dev.horizon.pyrite.exchange.ExchangeInterceptor;
import dev.horizon.pyrite.observer.LifecycleObserver;
import dev.horizon.pyrite.schema.v1.Declaration;
import dev.horizon.pyrite.schema.v1.Type;
import io.vertx.grpc.common.GrpcStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OptionsTest extends PyriteTest {

  private Deployment deployment(PyriteOptions options) {
    return new Deployment(pyrite.vertx, pyrite.database.pool(), pyrite.database.driver(), pyrite.openFga, options,
        new ExchangeInterceptor(), new LifecycleObserver<>("accounts"),
        new LifecycleObserver<>("sessions"));
  }

  private static Declaration without(Declaration declaration, String type) {
    Declaration.Builder builder = declaration.toBuilder().clearTypes();
    for (Type kept : declaration.getTypesList()) {
      if (!kept.getName().equals(type)) {
        builder.addTypes(kept);
      }
    }
    return builder.build();
  }

  @Test
  @DisplayName("a declaration with keys needs a root key")
  void root() {
    assertThatThrownBy(() -> deployment(new PyriteOptions()).write(Acme.declaration()).await(TIMEOUT))
        .isInstanceOfSatisfying(PyriteException.class, failure -> assertThat(failure.status())
            .isEqualTo(GrpcStatus.FAILED_PRECONDITION));
  }

  @Test
  @DisplayName("a declaration with accounts needs the options of authentication")
  void authentication() throws Exception {
    Aead root = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM).getPrimitive(RegistryConfiguration
        .get(), Aead.class);

    assertThatThrownBy(() -> deployment(new PyriteOptions().setKeyOptions(new KeyOptions().setRoot(root)))
        .write(Acme.declaration()).await(TIMEOUT))
        .isInstanceOfSatisfying(PyriteException.class, failure -> assertThat(failure.status())
            .isEqualTo(GrpcStatus.FAILED_PRECONDITION));
    assertThatThrownBy(() -> deployment(pyrite.options).write(without(Acme.declaration(), "pyrite.dev/Key"))
        .await(TIMEOUT))
        .isInstanceOfSatisfying(PyriteException.class, failure -> assertThat(failure.status())
            .isEqualTo(GrpcStatus.FAILED_PRECONDITION));
  }
}
