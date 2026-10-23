package dev.horizon.pyrite.schema;

import static dev.horizon.pyrite.Pyrite.ANNE;
import static dev.horizon.pyrite.Pyrite.status;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import dev.horizon.pyrite.Acme;
import dev.horizon.pyrite.Pyrite;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.PyriteServiceOptions;
import dev.horizon.pyrite.PyriteTest;
import dev.horizon.pyrite.data.v1.DeleteResourceRequest;
import dev.horizon.pyrite.data.v1.Resource;
import dev.horizon.pyrite.impl.resource.DeploymentImpl;
import dev.horizon.pyrite.schema.v1.Declaration;
import dev.horizon.pyrite.schema.v1.ReadDeclarationRequest;
import dev.horizon.pyrite.schema.v1.ReadDeclarationResponse;
import dev.horizon.pyrite.schema.v1.Type;
import dev.horizon.pyrite.schema.v1.WriteDeclarationRequest;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SchemaTest extends PyriteTest {

  private String write(Declaration declaration) throws Exception {
    return pyrite.schema.writeDeclaration(WriteDeclarationRequest.newBuilder().setDeclaration(declaration).build())
        .await(TIMEOUT).getId();
  }

  private ReadDeclarationResponse read(String id) throws Exception {
    return pyrite.schema.readDeclaration(ReadDeclarationRequest.newBuilder().setId(id).build()).await(TIMEOUT);
  }

  private static Declaration shortRetention() throws Exception {
    Declaration.Builder declaration = Acme.declaration().toBuilder();
    for (Type.Builder type : declaration.getTypesBuilderList()) {
      if (type.getName().equals("acme.dev/Project")) {
        type.getRetentionBuilder().setSeconds(Duration.ofDays(1).toSeconds());
      }
    }
    return declaration.build();
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

  @Nested
  @DisplayName("Writing")
  class Writing {

    @Test
    @DisplayName("the latest declaration is the one that every service reads")
    void latest() throws Exception {
      String first = read("").getId();
      pyrite.clock.move(Duration.ofSeconds(1));

      String second = write(shortRetention());

      assertThat(read("").getId()).isEqualTo(second).isNotEqualTo(first);
      pyrite.create(ANNE, "", "acme.dev/Organization", "acme", Map.of());
      pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of());
      Resource deleted = pyrite.data.deleteResource(DeleteResourceRequest.newBuilder().setSubject(ANNE)
          .setName("projects/ledger").setRevision(1).build()).await(TIMEOUT);
      assertThat(deleted.getFieldsMap().get("purge_time").getTimestampValue().getSeconds()).isEqualTo(pyrite.clock
          .instant().plus(Duration.ofDays(1)).getEpochSecond());
    }

    @Test
    @DisplayName("an earlier declaration is read back by its id, as it was written")
    void byId() throws Exception {
      String first = read("").getId();
      pyrite.clock.move(Duration.ofSeconds(1));
      write(shortRetention());

      assertThat(read(first).getDeclaration()).isEqualTo(Acme.declaration());
      assertThat(read(first).getCreateTime().getSeconds()).isEqualTo(Pyrite.START.getEpochSecond());
    }

    @Test
    @DisplayName("a declaration that breaks a rule is refused, and the latest stays")
    void refused() throws Exception {
      String latest = read("").getId();
      Declaration broken = Acme.declaration().toBuilder().addTypes(Type.newBuilder().setName("acme.dev/Folder"))
          .build();

      assertThat(status(() -> write(broken))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      assertThat(read("").getId()).isEqualTo(latest);
    }

    @Test
    @DisplayName("an empty declaration is refused because the Acme flows need accounts")
    void empty() throws Exception {
      assertThat(status(() -> write(Declaration.newBuilder().addAllFlows(Acme.declaration().getFlowsList())
          .build()))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }

  @Nested
  @DisplayName("Reading")
  class Reading {

    @Test
    @DisplayName("an unknown id is not found")
    void unknown() {
      assertThat(status(() -> read("00000000-0000-0000-0000-000000000000"))).isEqualTo(GrpcStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("before any declaration, reading finds none and every other call fails its precondition")
    void none() throws Exception {
      pyrite.database.pool().query("DELETE FROM pyrite_declaration").execute().await(TIMEOUT);

      assertThat(status(() -> read(""))).isEqualTo(GrpcStatus.NOT_FOUND);
      assertThat(status(() -> pyrite.create(ANNE, "", "acme.dev/Organization", "acme", Map.of())))
          .isEqualTo(GrpcStatus.FAILED_PRECONDITION);
      assertThat(status(() -> pyrite.authenticate("x.y"))).isEqualTo(GrpcStatus.FAILED_PRECONDITION);
    }
  }

  @Nested
  @DisplayName("Options")
  class Options {

    private DeploymentImpl deployment(PyriteServiceOptions options) {
      return new DeploymentImpl(pyrite.vertx, pyrite.database.pool(), pyrite.database.driver(), pyrite.openFga,
          options);
    }

    @Test
    @DisplayName("a declaration with keys needs a root key")
    void root() {
      assertThatThrownBy(() -> deployment(new PyriteServiceOptions()).write(Acme.declaration()))
          .isInstanceOfSatisfying(PyriteException.class, failure -> assertThat(failure.status())
              .isEqualTo(GrpcStatus.FAILED_PRECONDITION));
    }

    @Test
    @DisplayName("a declaration with accounts needs the options of authentication")
    void authentication() throws Exception {
      Aead root = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM).getPrimitive(RegistryConfiguration
          .get(), Aead.class);

      assertThatThrownBy(() -> deployment(new PyriteServiceOptions().withRoot(root)).write(Acme.declaration()))
          .isInstanceOfSatisfying(PyriteException.class, failure -> assertThat(failure.status())
              .isEqualTo(GrpcStatus.FAILED_PRECONDITION));
      assertThatThrownBy(() -> deployment(pyrite.options).write(without(Acme.declaration(), "pyrite.dev/Key")))
          .isInstanceOfSatisfying(PyriteException.class, failure -> assertThat(failure.status())
              .isEqualTo(GrpcStatus.FAILED_PRECONDITION));
    }
  }
}
