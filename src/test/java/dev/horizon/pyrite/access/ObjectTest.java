package dev.horizon.pyrite.access;

import static dev.horizon.pyrite.Pyrite.ANNE;
import static dev.horizon.pyrite.Pyrite.BOB;
import static dev.horizon.pyrite.Pyrite.status;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Struct;
import dev.horizon.pyrite.PyriteTest;
import dev.horizon.pyrite.access.v1.DeleteObjectRequest;
import io.vertx.grpc.common.GrpcStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ObjectTest extends PyriteTest {

  @BeforeEach
  void open() throws Exception {
    pyrite.create(ANNE, "organizations/acme", "");
  }

  private void delete(String subject, String name) throws Exception {
    pyrite.access.deleteObject(DeleteObjectRequest.newBuilder().setSubject(subject).setName(name).build())
        .await(TIMEOUT);
  }

  @Nested
  @DisplayName("Creating")
  class Creating {

    @Test
    @DisplayName("a resource under a parent inherits what is granted on the parent")
    void inherits() throws Exception {
      pyrite.grant(ANNE, "organizations/acme", "member", BOB);
      pyrite.create(ANNE, "projects/ledger", "organizations/acme");

      assertThat(pyrite.check(BOB, "projects/ledger", "can_view", Struct.getDefaultInstance())).isTrue();
      assertThat(pyrite.check(ANNE, "projects/ledger", "owner", Struct.getDefaultInstance())).isTrue();
    }

    @Test
    @DisplayName("a resource is made known once")
    void once() throws Exception {
      assertThat(status(() -> pyrite.create(BOB, "organizations/acme", ""))).isEqualTo(GrpcStatus.ALREADY_EXISTS);
      assertThat(pyrite.check(BOB, "organizations/acme", "admin", Struct.getDefaultInstance())).isFalse();
    }

    @Test
    @DisplayName("creating under a parent needs the type's create permission on it")
    void permission() {
      assertThat(status(() -> pyrite.create(BOB, "projects/ledger", "organizations/acme")))
          .isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("a parent is known and of one of the type's parent types")
    void parent() throws Exception {
      pyrite.create(ANNE, "projects/ledger", "organizations/acme");

      assertThat(status(() -> pyrite.create(ANNE, "projects/payroll", "organizations/globex")))
          .isEqualTo(GrpcStatus.NOT_FOUND);
      assertThat(status(() -> pyrite.create(ANNE, "projects/payroll", "projects/ledger")))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      assertThat(status(() -> pyrite.create(ANNE, "projects/payroll", ""))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      assertThat(status(() -> pyrite.create(ANNE, "organizations/globex", "organizations/acme")))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("a name is of a declared type, with an id that OpenFGA takes")
    void name() {
      assertThat(status(() -> pyrite.create(ANNE, "folders/docs", ""))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      assertThat(status(() -> pyrite.create(ANNE, "organizations/Acme Corp", "")))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }

  @Nested
  @DisplayName("Deleting")
  class Deleting {

    @Test
    @DisplayName("a forgotten resource keeps no grant, and its name can be made known again")
    void forgets() throws Exception {
      pyrite.create(ANNE, "projects/ledger", "organizations/acme");
      pyrite.grant(ANNE, "projects/ledger", "viewer", BOB);

      delete(ANNE, "projects/ledger");

      assertThat(status(() -> pyrite.check(BOB, "projects/ledger", "can_view", Struct.getDefaultInstance())))
          .isEqualTo(GrpcStatus.NOT_FOUND);
      pyrite.create(ANNE, "projects/ledger", "organizations/acme");
      assertThat(pyrite.check(BOB, "projects/ledger", "can_view", Struct.getDefaultInstance())).isFalse();
    }

    @Test
    @DisplayName("a resource with resources under it is not forgotten")
    void occupied() throws Exception {
      pyrite.create(ANNE, "projects/ledger", "organizations/acme");

      assertThat(status(() -> delete(ANNE, "organizations/acme"))).isEqualTo(GrpcStatus.FAILED_PRECONDITION);
      delete(ANNE, "projects/ledger");
      delete(ANNE, "organizations/acme");
    }

    @Test
    @DisplayName("an unknown resource is not found")
    void unknown() {
      assertThat(status(() -> delete(ANNE, "projects/missing"))).isEqualTo(GrpcStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("forgetting a resource needs the type's grant permission on it")
    void permission() throws Exception {
      pyrite.create(ANNE, "projects/ledger", "organizations/acme");
      pyrite.grant(ANNE, "projects/ledger", "editor", BOB);

      assertThat(status(() -> delete(BOB, "projects/ledger"))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
      assertThat(pyrite.check(BOB, "projects/ledger", "editor", Struct.getDefaultInstance())).isTrue();
    }
  }

  @Nested
  @DisplayName("Listing")
  class Listing {

    @Test
    @DisplayName("the objects of a type are those the subject has the permission on, through its parents too")
    void listed() throws Exception {
      pyrite.create(ANNE, "projects/ledger", "organizations/acme");
      pyrite.create(ANNE, "projects/payroll", "organizations/acme");
      pyrite.create(BOB, "organizations/globex", "");
      pyrite.create(BOB, "projects/mars", "organizations/globex");
      pyrite.grant(BOB, "projects/mars", "viewer", ANNE);

      assertThat(pyrite.objects(ANNE, "acme.dev/Project", "can_view", Struct.getDefaultInstance()))
          .containsExactly("projects/ledger", "projects/mars", "projects/payroll");
      assertThat(pyrite.objects(BOB, "acme.dev/Project", "can_view", Struct.getDefaultInstance()))
          .containsExactly("projects/mars");
    }

    @Test
    @DisplayName("a permission is a relation of the type")
    void permission() {
      assertThat(status(() -> pyrite.objects(ANNE, "acme.dev/Project", "can_fly", Struct.getDefaultInstance())))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }
}
