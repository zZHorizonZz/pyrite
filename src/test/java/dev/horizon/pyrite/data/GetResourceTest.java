package dev.horizon.pyrite.data;

import static dev.horizon.pyrite.Pyrite.ANNE;
import static dev.horizon.pyrite.Pyrite.BOB;
import static dev.horizon.pyrite.Pyrite.CAROL;
import static dev.horizon.pyrite.Pyrite.status;
import static dev.horizon.pyrite.Pyrite.text;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.PyriteTest;
import dev.horizon.pyrite.access.v1.DeleteGrantRequest;
import dev.horizon.pyrite.access.v1.Grant;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GetResourceTest extends PyriteTest {

  @BeforeEach
  void open() throws Exception {
    pyrite.create(ANNE, "", "acme.dev/Organization", "acme", Map.of());
    pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of("billing_account", text("b-1")));
  }

  @Nested
  @DisplayName("Permissions")
  class Permissions {

    @Test
    @DisplayName("the subject needs the type's read permission")
    void read() throws Exception {
      assertThat(pyrite.get(ANNE, "projects/ledger").getFieldsMap().get("billing_account").getStringValue())
          .isEqualTo("b-1");
      assertThat(status(() -> pyrite.get(BOB, "projects/ledger"))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("the read permission can come from the parent")
    void inherited() throws Exception {
      pyrite.grant(ANNE, "organizations/acme", "member", BOB);

      assertThat(pyrite.get(BOB, "projects/ledger").getName()).isEqualTo("projects/ledger");
      assertThat(status(() -> pyrite.get(CAROL, "projects/ledger"))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("a revoked grant no longer reads")
    void revoked() throws Exception {
      pyrite.grant(ANNE, "projects/ledger", "viewer", BOB);
      pyrite.access.deleteGrant(DeleteGrantRequest.newBuilder().setSubject(ANNE).setGrant(Grant.newBuilder()
          .setObject("projects/ledger").setRelation("viewer").setGrantee(BOB)).build()).await(TIMEOUT);

      assertThat(status(() -> pyrite.get(BOB, "projects/ledger"))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }

    @ParameterizedTest
    @ValueSource(strings = { "bob", "accounts/bob", "user:", ":bob", "" })
    @DisplayName("the subject is a principal")
    void subject(String subject) {
      assertThat(status(() -> pyrite.get(subject, "projects/ledger"))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }

  @Nested
  @DisplayName("Names")
  class Names {

    @Test
    @DisplayName("a missing resource is not found, whoever asks")
    void missing() {
      assertThat(status(() -> pyrite.get(ANNE, "projects/payroll"))).isEqualTo(GrpcStatus.NOT_FOUND);
      assertThat(status(() -> pyrite.get(BOB, "projects/payroll"))).isEqualTo(GrpcStatus.NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = { "folders/docs", "ledger", "projects/", "/ledger" })
    @DisplayName("a name of no declared type is refused")
    void undeclared(String name) {
      assertThat(status(() -> pyrite.get(ANNE, name))).isIn(GrpcStatus.INVALID_ARGUMENT, GrpcStatus.NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("Fields")
  class Fields {

    @Test
    @DisplayName("a key version never returns its material")
    void keyMaterial() throws Exception {
      pyrite.key("organizations/acme", "payments", "ENCRYPTION", "AES256_GCM");

      assertThat(pyrite.get(ANNE, "keyVersions/payments-1").getFieldsMap()).containsKeys("number", "state")
          .doesNotContainKeys("sealed", "public_key_info");
    }
  }
}
