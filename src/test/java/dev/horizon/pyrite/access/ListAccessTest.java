package dev.horizon.pyrite.access;

import static dev.horizon.pyrite.Pyrite.ANNE;
import static dev.horizon.pyrite.Pyrite.BOB;
import static dev.horizon.pyrite.Pyrite.CAROL;
import static dev.horizon.pyrite.Pyrite.status;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Timestamp;
import dev.horizon.pyrite.PyriteTest;
import dev.horizon.pyrite.access.v1.DeleteGrantRequest;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.access.v1.ListChangesRequest;
import dev.horizon.pyrite.access.v1.ListGrantsRequest;
import dev.horizon.pyrite.access.v1.ListGrantsResponse;
import dev.horizon.pyrite.access.v1.ListSubjectsRequest;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ListAccessTest extends PyriteTest {

  @BeforeEach
  void open() throws Exception {
    pyrite.create(ANNE, "", "acme.dev/Organization", "acme", Map.of());
    pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of());
    pyrite.create(ANNE, "organizations/acme", "acme.dev/Group", "finance", Map.of());
  }

  private ListSubjectsRequest.Builder subjects(String subject) {
    return ListSubjectsRequest.newBuilder().setSubject(subject).setObject("projects/ledger").setPermission(
        "can_view").setSubjectType("user");
  }

  private List<String> changes(String subject, Timestamp start) throws Exception {
    ListChangesRequest.Builder request = ListChangesRequest.newBuilder().setSubject(subject)
        .setType("acme.dev/Project");
    if (start != null) {
      request.setStartTime(start);
    }
    return pyrite.access.listChanges(request.build()).await(TIMEOUT).getChangesList().stream()
        .map(change -> change.getGrant().getRelation() + " " + change.getGrant().getGrantee() + " "
            + change.getOperation())
        .toList();
  }

  @Nested
  @DisplayName("Grants")
  class Grants {

    @Test
    @DisplayName("grants are listed by relation and by grantee")
    void filtered() throws Exception {
      pyrite.grant(ANNE, "projects/ledger", "viewer", BOB);
      pyrite.grant(ANNE, "projects/ledger", "viewer", CAROL);
      pyrite.grant(ANNE, "projects/ledger", "editor", BOB);

      assertThat(pyrite.access.listGrants(ListGrantsRequest.newBuilder().setSubject(ANNE)
          .setObject("projects/ledger").setRelation("viewer").build()).await(TIMEOUT).getGrantsList())
          .extracting(Grant::getGrantee).containsExactlyInAnyOrder(BOB, CAROL);
      assertThat(pyrite.access.listGrants(ListGrantsRequest.newBuilder().setSubject(ANNE)
          .setObject("projects/ledger").setGrantee(BOB).build()).await(TIMEOUT).getGrantsList())
          .extracting(Grant::getRelation).containsExactlyInAnyOrder("viewer", "editor");
    }

    @Test
    @DisplayName("grants are paged, each once")
    void paged() throws Exception {
      for (String user : new String[] { BOB, CAROL, "user:dave", "user:erin" }) {
        pyrite.grant(ANNE, "projects/ledger", "viewer", user);
      }
      List<String> grantees = new ArrayList<>();
      String token = "";
      do {
        ListGrantsResponse page = pyrite.access.listGrants(ListGrantsRequest.newBuilder().setSubject(ANNE)
            .setObject("projects/ledger").setPageSize(2).setPageToken(token).build()).await(TIMEOUT);
        assertThat(page.getGrantsCount()).isLessThanOrEqualTo(2);
        page.getGrantsList().forEach(grant -> grantees.add(grant.getGrantee()));
        token = page.getNextPageToken();
      } while (!token.isEmpty());

      assertThat(grantees).containsExactlyInAnyOrder(ANNE, BOB, CAROL, "user:dave", "user:erin");
    }

    @Test
    @DisplayName("listing grants needs the grant permission, even for a grantee")
    void permission() throws Exception {
      pyrite.grant(ANNE, "projects/ledger", "editor", BOB);

      assertThat(status(() -> pyrite.access.listGrants(ListGrantsRequest.newBuilder().setSubject(BOB)
          .setObject("projects/ledger").build()).await(TIMEOUT))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }
  }

  @Nested
  @DisplayName("Subjects")
  class Subjects {

    @Test
    @DisplayName("subjects include the members of groups and of ancestors")
    void members() throws Exception {
      pyrite.grant(ANNE, "organizations/acme", "member", BOB);
      pyrite.grant(ANNE, "groups/finance", "member", CAROL);
      pyrite.grant(ANNE, "projects/ledger", "viewer", "group:finance#member");

      assertThat(pyrite.access.listSubjects(subjects(ANNE).build()).await(TIMEOUT).getSubjectsList())
          .containsExactlyInAnyOrder(ANNE, BOB, CAROL);
      assertThat(pyrite.access.listSubjects(subjects(ANNE).setPermission("viewer").setSubjectType("group#member")
          .build()).await(TIMEOUT).getSubjectsList()).containsExactly("group:finance#member");
    }

    @Test
    @DisplayName("a blocked principal is not a subject")
    void blocked() throws Exception {
      pyrite.grant(ANNE, "organizations/acme", "member", BOB);
      pyrite.grant(ANNE, "projects/ledger", "blocked", BOB);

      assertThat(pyrite.access.listSubjects(subjects(ANNE).build()).await(TIMEOUT).getSubjectsList())
          .containsExactly(ANNE);
    }

    @Test
    @DisplayName("listing subjects needs a type and the grant permission")
    void refused() throws Exception {
      pyrite.grant(ANNE, "projects/ledger", "viewer", BOB);

      assertThat(status(() -> pyrite.access.listSubjects(subjects(ANNE).clearSubjectType().build())
          .await(TIMEOUT))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      assertThat(status(() -> pyrite.access.listSubjects(subjects(BOB).build()).await(TIMEOUT)))
          .isEqualTo(GrpcStatus.PERMISSION_DENIED);
      assertThat(status(() -> pyrite.access.listSubjects(subjects(ANNE).setPermission("flies").build())
          .await(TIMEOUT))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }

  @Nested
  @DisplayName("Changes")
  class Changes {

    @Test
    @DisplayName("changes are listed oldest first")
    void ordered() throws Exception {
      pyrite.grant(ANNE, "projects/ledger", "viewer", BOB);
      pyrite.clock.move(Duration.ofHours(1));
      pyrite.access.deleteGrant(DeleteGrantRequest.newBuilder().setSubject(ANNE).setGrant(Grant.newBuilder()
          .setObject("projects/ledger").setRelation("viewer").setGrantee(BOB)).build()).await(TIMEOUT);

      assertThat(changes(ANNE, null)).containsExactly("owner user:anne GRANTED", "viewer user:bob GRANTED",
          "viewer user:bob REVOKED");
    }

    @Test
    @DisplayName("a change is listed only to who may grant on its resource")
    void permission() throws Exception {
      pyrite.grant(ANNE, "projects/ledger", "viewer", BOB);

      assertThat(changes(BOB, null)).isEmpty();
      assertThat(changes(ANNE, null)).hasSize(2);
    }

    @Test
    @DisplayName("a type that is not declared is refused")
    void undeclared() {
      assertThat(status(() -> pyrite.access.listChanges(ListChangesRequest.newBuilder().setSubject(ANNE)
          .setType("acme.dev/Folder").build()).await(TIMEOUT))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }
}
