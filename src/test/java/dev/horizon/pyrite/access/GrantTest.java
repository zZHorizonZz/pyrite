package dev.horizon.pyrite.access;

import static dev.horizon.pyrite.Pyrite.ANNE;
import static dev.horizon.pyrite.Pyrite.BOB;
import static dev.horizon.pyrite.Pyrite.CAROL;
import static dev.horizon.pyrite.Pyrite.status;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import dev.horizon.pyrite.PyriteTest;
import dev.horizon.pyrite.access.v1.Condition;
import dev.horizon.pyrite.access.v1.CreateGrantRequest;
import dev.horizon.pyrite.access.v1.DeleteGrantRequest;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.access.v1.ListGrantsRequest;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GrantTest extends PyriteTest {

  @BeforeEach
  void open() throws Exception {
    pyrite.create(ANNE, "organizations/acme", "");
    pyrite.create(ANNE, "projects/ledger", "organizations/acme");
    pyrite.create(ANNE, "groups/finance", "organizations/acme");
    pyrite.create(ANNE, "groups/audit", "organizations/acme");
  }

  private List<String> grants(String object) throws Exception {
    return pyrite.access.listGrants(ListGrantsRequest.newBuilder().setSubject(ANNE).setObject(object).build())
        .await(TIMEOUT).getGrantsList().stream().map(grant -> grant.getRelation() + " " + grant.getGrantee())
        .sorted().toList();
  }

  private void revoke(String subject, String object, String relation, String grantee) throws Exception {
    pyrite.access.deleteGrant(DeleteGrantRequest.newBuilder().setSubject(subject).setGrant(Grant.newBuilder()
        .setObject(object).setRelation(relation).setGrantee(grantee)).build()).await(TIMEOUT);
  }

  private Grant conditional(String relation, String condition, Struct context) throws Exception {
    return pyrite.access.createGrant(CreateGrantRequest.newBuilder().setSubject(ANNE).setGrant(Grant.newBuilder()
        .setObject("projects/ledger").setRelation(relation).setGrantee(BOB).setCondition(Condition.newBuilder()
            .setName(condition).setContext(context)))
        .build()).await(TIMEOUT);
  }

  private static Struct expires(Value value) {
    return Struct.newBuilder().putFields("expires_at", value).build();
  }

  @Nested
  @DisplayName("Grant and revoke")
  class GrantAndRevoke {

    @Test
    @DisplayName("a grant gives the relation, and a revoke takes it back")
    void roundTrip() throws Exception {
      Grant granted = pyrite.grant(ANNE, "projects/ledger", "viewer", BOB);

      assertThat(granted.getObject()).isEqualTo("projects/ledger");
      assertThat(granted.getRelation()).isEqualTo("viewer");
      assertThat(granted.getGrantee()).isEqualTo(BOB);
      assertThat(pyrite.check(BOB, "projects/ledger", "can_view", Struct.getDefaultInstance())).isTrue();
      revoke(ANNE, "projects/ledger", "viewer", BOB);
      assertThat(pyrite.check(BOB, "projects/ledger", "can_view", Struct.getDefaultInstance())).isFalse();
    }

    @Test
    @DisplayName("granting twice and revoking twice change nothing")
    void idempotent() throws Exception {
      pyrite.grant(ANNE, "projects/ledger", "viewer", BOB);
      pyrite.grant(ANNE, "projects/ledger", "viewer", BOB);
      assertThat(grants("projects/ledger")).containsExactly("owner user:anne", "viewer user:bob");

      revoke(ANNE, "projects/ledger", "viewer", BOB);
      revoke(ANNE, "projects/ledger", "viewer", BOB);
      assertThat(grants("projects/ledger")).containsExactly("owner user:anne");
    }

    @Test
    @DisplayName("a wildcard grants every user")
    void wildcard() throws Exception {
      pyrite.grant(ANNE, "projects/ledger", "viewer", "user:*");

      assertThat(pyrite.check("user:anyone", "projects/ledger", "can_view", Struct.getDefaultInstance())).isTrue();
    }
  }

  @Nested
  @DisplayName("Groups")
  class Groups {

    @Test
    @DisplayName("a group grants what is granted to its members, through nested groups")
    void members() throws Exception {
      pyrite.grant(ANNE, "groups/audit", "member", CAROL);
      pyrite.grant(ANNE, "groups/finance", "member", "group:audit#member");
      pyrite.grant(ANNE, "projects/ledger", "viewer", "group:finance#member");

      assertThat(pyrite.check(CAROL, "projects/ledger", "can_view", Struct.getDefaultInstance())).isTrue();
      revoke(ANNE, "groups/audit", "member", CAROL);
      assertThat(pyrite.check(CAROL, "projects/ledger", "can_view", Struct.getDefaultInstance())).isFalse();
    }

    @Test
    @DisplayName("a grant that makes a cycle of groups is refused")
    void cycle() throws Exception {
      pyrite.grant(ANNE, "groups/finance", "member", "group:audit#member");

      assertThat(status(() -> pyrite.grant(ANNE, "groups/audit", "member", "group:finance#member")))
          .isEqualTo(GrpcStatus.FAILED_PRECONDITION);
      assertThat(status(() -> pyrite.grant(ANNE, "groups/finance", "member", "group:finance#member")))
          .isEqualTo(GrpcStatus.FAILED_PRECONDITION);
    }
  }

  @Nested
  @DisplayName("Refusals")
  class Refusals {

    @Test
    @DisplayName("granting and revoking need the type's grant permission")
    void permission() throws Exception {
      pyrite.grant(ANNE, "projects/ledger", "editor", BOB);

      assertThat(status(() -> pyrite.grant(BOB, "projects/ledger", "viewer", CAROL)))
          .isEqualTo(GrpcStatus.PERMISSION_DENIED);
      assertThat(status(() -> revoke(BOB, "projects/ledger", "editor", BOB))).isEqualTo(
          GrpcStatus.PERMISSION_DENIED);
      assertThat(status(() -> pyrite.grant(BOB, "projects/ledger", "owner", BOB)))
          .isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }

    @ParameterizedTest
    @ValueSource(strings = { "can_view", "can_edit", "flies", "parent", "" })
    @DisplayName("only a grantable relation is granted")
    void grantable(String relation) {
      assertThat(status(() -> pyrite.grant(ANNE, "projects/ledger", relation, BOB)))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @ParameterizedTest
    @ValueSource(strings = { "user:*", "project:payroll", "group:finance", "group:finance#manager", "folder:docs",
        "bob", "", "user:bob#member" })
    @DisplayName("a grantee is of a type that the relation takes")
    void grantee(String grantee) {
      assertThat(status(() -> pyrite.grant(ANNE, "projects/ledger", "owner", grantee)))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("a grant on a missing resource is not found")
    void missing() {
      assertThat(status(() -> pyrite.grant(ANNE, "projects/payroll", "viewer", BOB)))
          .isEqualTo(GrpcStatus.NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("Conditions")
  class Conditions {

    @Test
    @DisplayName("a condition goes only on a relation that takes it")
    void taken() {
      assertThat(status(() -> conditional("viewer", "not_expired", expires(Value.newBuilder()
          .setStringValue("2026-06-01T00:00:00Z").build())))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("a condition is one that the declaration declares")
    void declared() {
      assertThat(status(() -> conditional("editor", "on_weekdays", Struct.getDefaultInstance())))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("a condition's values have the types of its parameters")
    void parameters() {
      assertThat(status(() -> conditional("editor", "not_expired", expires(Value.newBuilder().setStringValue(
          "next week").build())))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      assertThat(status(() -> conditional("editor", "not_expired", Struct.newBuilder().putFields("budget", Value
          .newBuilder().setNumberValue(3).build()).build()))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }
}
