package dev.horizon.pyrite.access;

import static dev.horizon.pyrite.Pyrite.ANNE;
import static dev.horizon.pyrite.Pyrite.BOB;
import static dev.horizon.pyrite.Pyrite.CAROL;
import static dev.horizon.pyrite.Pyrite.at;
import static dev.horizon.pyrite.Pyrite.status;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Struct;
import dev.horizon.pyrite.PyriteTest;
import dev.horizon.pyrite.access.v1.CheckRequest;
import dev.horizon.pyrite.access.v1.Condition;
import dev.horizon.pyrite.access.v1.CreateGrantRequest;
import dev.horizon.pyrite.access.v1.Grant;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CheckTest extends PyriteTest {

  @BeforeEach
  void open() throws Exception {
    pyrite.create(ANNE, "", "acme.dev/Organization", "acme", Map.of());
    pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of());
  }

  private boolean check(String subject, String principal, String permission, Struct context) throws Exception {
    return pyrite.access.check(CheckRequest.newBuilder().setSubject(subject).setPrincipal(principal)
        .setObject("projects/ledger").setPermission(permission).setContext(context).build()).await(TIMEOUT)
        .getAllowed();
  }

  @Nested
  @DisplayName("Principals")
  class Principals {

    @Test
    @DisplayName("a subject checks itself without the grant permission")
    void self() throws Exception {
      pyrite.grant(ANNE, "organizations/acme", "member", BOB);

      assertThat(pyrite.check(BOB, "projects/ledger", "can_view", Struct.getDefaultInstance())).isTrue();
      assertThat(pyrite.check(BOB, "projects/ledger", "can_edit", Struct.getDefaultInstance())).isFalse();
    }

    @Test
    @DisplayName("asking about another principal needs the grant permission")
    void other() throws Exception {
      pyrite.grant(ANNE, "organizations/acme", "member", BOB);

      assertThat(check(ANNE, BOB, "can_view", Struct.getDefaultInstance())).isTrue();
      assertThat(check(ANNE, CAROL, "can_view", Struct.getDefaultInstance())).isFalse();
      assertThat(status(() -> check(BOB, ANNE, "can_view", Struct.getDefaultInstance())))
          .isEqualTo(GrpcStatus.PERMISSION_DENIED);
      assertThat(status(() -> check(BOB, CAROL, "can_view", Struct.getDefaultInstance())))
          .isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("a principal is of the form type:id")
    void form() {
      assertThat(status(() -> check(ANNE, "bob", "can_view", Struct.getDefaultInstance())))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }

  @Nested
  @DisplayName("Hierarchy")
  class Hierarchy {

    @Test
    @DisplayName("a grant on the organization reaches its projects")
    void inherited() throws Exception {
      pyrite.grant(ANNE, "organizations/acme", "admin", BOB);

      assertThat(check(ANNE, BOB, "can_edit", Struct.getDefaultInstance())).isTrue();
      assertThat(check(ANNE, BOB, "owner", Struct.getDefaultInstance())).isFalse();
    }

    @Test
    @DisplayName("a block on the organization overrides a grant on the project")
    void block() throws Exception {
      pyrite.grant(ANNE, "projects/ledger", "editor", BOB);
      pyrite.grant(ANNE, "organizations/acme", "blocked", BOB);

      assertThat(check(ANNE, BOB, "can_edit", Struct.getDefaultInstance())).isFalse();
      assertThat(check(ANNE, BOB, "editor", Struct.getDefaultInstance())).isTrue();
    }
  }

  @Nested
  @DisplayName("Conditions")
  class Conditions {

    @BeforeEach
    void expiring() throws Exception {
      pyrite.access.createGrant(CreateGrantRequest.newBuilder().setSubject(ANNE).setGrant(Grant.newBuilder()
          .setObject("projects/ledger").setRelation("editor").setGrantee(BOB).setCondition(Condition.newBuilder()
              .setName("not_expired").setContext(Struct.newBuilder().putFields("expires_at",
                  com.google.protobuf.Value.newBuilder().setStringValue("2026-06-01T00:00:00Z").build()))))
          .build()).await(TIMEOUT);
    }

    @Test
    @DisplayName("a condition holds until it no longer does")
    void evaluated() throws Exception {
      assertThat(check(ANNE, BOB, "can_edit", at("2026-05-01T00:00:00Z"))).isTrue();
      assertThat(check(ANNE, BOB, "can_edit", at("2026-06-01T00:00:00Z"))).isFalse();
      assertThat(check(ANNE, BOB, "can_edit", at("2026-07-01T00:00:00Z"))).isFalse();
    }

    @Test
    @DisplayName("a check cannot override the values that the grant holds")
    void fixed() throws Exception {
      Struct forged = at("2026-07-01T00:00:00Z").toBuilder().putFields("expires_at", com.google.protobuf.Value
          .newBuilder().setStringValue("2030-01-01T00:00:00Z").build()).build();

      assertThat(check(ANNE, BOB, "can_edit", forged)).isFalse();
    }
  }

  @Nested
  @DisplayName("Refusals")
  class Refusals {

    @Test
    @DisplayName("a check names a relation of the type")
    void relation() {
      assertThat(status(() -> check(ANNE, BOB, "flies", Struct.getDefaultInstance())))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("a check on a missing resource is not found")
    void missing() {
      assertThat(status(() -> pyrite.check(ANNE, "projects/payroll", "can_view", Struct.getDefaultInstance())))
          .isEqualTo(GrpcStatus.NOT_FOUND);
    }
  }
}
