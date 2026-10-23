package dev.horizon.pyrite.data;

import static dev.horizon.pyrite.Pyrite.ANNE;
import static dev.horizon.pyrite.Pyrite.BOB;
import static dev.horizon.pyrite.Pyrite.DAVE;
import static dev.horizon.pyrite.Pyrite.status;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.PyriteTest;
import dev.horizon.pyrite.data.v1.MoveResourceRequest;
import dev.horizon.pyrite.data.v1.Resource;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MoveResourceTest extends PyriteTest {

  @BeforeEach
  void open() throws Exception {
    pyrite.create(ANNE, "", "acme.dev/Organization", "acme", Map.of());
    pyrite.create(DAVE, "", "acme.dev/Organization", "globex", Map.of());
    pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of());
  }

  private Resource move(String subject, String name, String parent, long revision) throws Exception {
    return pyrite.data.moveResource(MoveResourceRequest.newBuilder().setSubject(subject).setName(name)
        .setParent(parent).setRevision(revision).build()).await(TIMEOUT);
  }

  @Nested
  @DisplayName("Permissions")
  class Permissions {

    @Test
    @DisplayName("a move needs the create permission on the new parent")
    void create() throws Exception {
      assertThat(status(() -> move(ANNE, "projects/ledger", "organizations/globex", 1)))
          .isEqualTo(GrpcStatus.PERMISSION_DENIED);
      pyrite.grant(DAVE, "organizations/globex", "admin", ANNE);

      Resource moved = move(ANNE, "projects/ledger", "organizations/globex", 1);

      assertThat(moved.getName()).isEqualTo("projects/ledger");
      assertThat(moved.getFieldsMap().get("parent").getStringValue()).isEqualTo("organizations/globex");
      assertThat(moved.getFieldsMap().get("revision").getIntegerValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("a move needs the update permission on the resource")
    void update() throws Exception {
      pyrite.grant(DAVE, "organizations/globex", "admin", BOB);
      pyrite.grant(ANNE, "projects/ledger", "viewer", BOB);

      assertThat(status(() -> move(BOB, "projects/ledger", "organizations/globex", 1)))
          .isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }
  }

  @Nested
  @DisplayName("Inheritance")
  class Inheritance {

    @Test
    @DisplayName("what the resource inherits follows the move at once")
    void follows() throws Exception {
      pyrite.grant(ANNE, "organizations/acme", "member", BOB);
      pyrite.grant(DAVE, "organizations/globex", "admin", ANNE);
      assertThat(pyrite.get(BOB, "projects/ledger").getName()).isEqualTo("projects/ledger");

      move(ANNE, "projects/ledger", "organizations/globex", 1);

      assertThat(status(() -> pyrite.get(BOB, "projects/ledger"))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
      assertThat(pyrite.get(DAVE, "projects/ledger").getName()).isEqualTo("projects/ledger");
      assertThat(pyrite.get(ANNE, "projects/ledger").getName()).isEqualTo("projects/ledger");
    }
  }

  @Nested
  @DisplayName("Parents")
  class Parents {

    @Test
    @DisplayName("a resource moves only under a parent of a parent type")
    void type() throws Exception {
      pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "payroll", Map.of());

      assertThat(status(() -> move(ANNE, "projects/ledger", "projects/payroll", 1)))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("a move names an existing parent")
    void existing() {
      assertThat(status(() -> move(ANNE, "projects/ledger", "", 1))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      assertThat(status(() -> move(ANNE, "projects/ledger", "organizations/initech", 1)))
          .isEqualTo(GrpcStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("a resource of a type without parents does not move")
    void topLevel() {
      assertThat(status(() -> move(ANNE, "organizations/acme", "organizations/globex", 1)))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

  }

  @Nested
  @DisplayName("Revisions")
  class Revisions {

    @Test
    @DisplayName("a move at another revision is aborted")
    void stale() throws Exception {
      pyrite.grant(DAVE, "organizations/globex", "admin", ANNE);

      assertThat(status(() -> move(ANNE, "projects/ledger", "organizations/globex", 2)))
          .isEqualTo(GrpcStatus.ABORTED);
      assertThat(pyrite.get(ANNE, "projects/ledger").getFieldsMap().get("parent").getStringValue())
          .isEqualTo("organizations/acme");
    }
  }
}
