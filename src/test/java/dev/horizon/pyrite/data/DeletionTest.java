package dev.horizon.pyrite.data;

import static dev.horizon.pyrite.Pyrite.ANNE;
import static dev.horizon.pyrite.Pyrite.BOB;
import static dev.horizon.pyrite.Pyrite.START;
import static dev.horizon.pyrite.Pyrite.status;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.PyriteTest;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.access.v1.ListGrantsRequest;
import dev.horizon.pyrite.data.v1.DeleteResourceRequest;
import dev.horizon.pyrite.data.v1.PurgeResourcesRequest;
import dev.horizon.pyrite.data.v1.Resource;
import dev.horizon.pyrite.data.v1.UndeleteResourceRequest;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DeletionTest extends PyriteTest {

  @BeforeEach
  void open() throws Exception {
    pyrite.create(ANNE, "", "acme.dev/Organization", "acme", Map.of());
    pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of());
  }

  private Resource delete(String subject, String name, long revision) throws Exception {
    return pyrite.data.deleteResource(DeleteResourceRequest.newBuilder().setSubject(subject).setName(name)
        .setRevision(revision).build()).await(TIMEOUT);
  }

  private Resource undelete(String subject, String name, long revision) throws Exception {
    return pyrite.data.undeleteResource(UndeleteResourceRequest.newBuilder().setSubject(subject).setName(name)
        .setRevision(revision).build()).await(TIMEOUT);
  }

  private long purged() throws Exception {
    return pyrite.data.purgeResources(PurgeResourcesRequest.getDefaultInstance()).await(TIMEOUT).getPurged();
  }

  @Nested
  @DisplayName("Delete")
  class Delete {

    @Test
    @DisplayName("a deleted resource waits for the retention of its type")
    void retention() throws Exception {
      Resource deleted = delete(ANNE, "projects/ledger", 1);

      assertThat(deleted.getFieldsMap().get("delete_time").getTimestampValue().getSeconds())
          .isEqualTo(START.getEpochSecond());
      assertThat(deleted.getFieldsMap().get("purge_time").getTimestampValue().getSeconds())
          .isEqualTo(START.plus(Duration.ofDays(30)).getEpochSecond());
      assertThat(deleted.getFieldsMap().get("revision").getIntegerValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("the subject needs the type's delete permission")
    void permission() throws Exception {
      pyrite.grant(ANNE, "projects/ledger", "editor", BOB);

      assertThat(status(() -> delete(BOB, "projects/ledger", 1))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("a delete at another revision is aborted")
    void stale() {
      assertThat(status(() -> delete(ANNE, "projects/ledger", 2))).isEqualTo(GrpcStatus.ABORTED);
    }

    @Test
    @DisplayName("a resource with active resources beneath it is not deleted")
    void children() throws Exception {
      assertThat(status(() -> delete(ANNE, "organizations/acme", 1))).isEqualTo(GrpcStatus.FAILED_PRECONDITION);

      delete(ANNE, "projects/ledger", 1);
      assertThat(delete(ANNE, "organizations/acme", 1).getFieldsMap().get("delete_time").hasTimestampValue())
          .isTrue();
    }

    @Test
    @DisplayName("a deleted resource is not deleted again and takes no new children")
    void again() throws Exception {
      delete(ANNE, "projects/ledger", 1);

      assertThat(status(() -> delete(ANNE, "projects/ledger", 2))).isEqualTo(GrpcStatus.FAILED_PRECONDITION);
      delete(ANNE, "organizations/acme", 1);
      assertThat(status(() -> pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "payroll", Map.of())))
          .isEqualTo(GrpcStatus.FAILED_PRECONDITION);
    }
  }

  @Nested
  @DisplayName("Undelete")
  class Undelete {

    @Test
    @DisplayName("an undelete before the purge keeps the grants")
    void keeps() throws Exception {
      pyrite.grant(ANNE, "projects/ledger", "viewer", BOB);
      delete(ANNE, "projects/ledger", 1);
      pyrite.clock.move(Duration.ofDays(29));

      Resource back = undelete(ANNE, "projects/ledger", 2);

      assertThat(back.getFieldsMap().get("delete_time").hasNullValue()).isTrue();
      assertThat(back.getFieldsMap().get("purge_time").hasNullValue()).isTrue();
      assertThat(pyrite.get(BOB, "projects/ledger").getName()).isEqualTo("projects/ledger");
    }

    @Test
    @DisplayName("an active resource is not undeleted")
    void active() {
      assertThat(status(() -> undelete(ANNE, "projects/ledger", 1))).isEqualTo(GrpcStatus.FAILED_PRECONDITION);
    }

    @Test
    @DisplayName("an undelete after the retention is refused")
    void late() throws Exception {
      delete(ANNE, "projects/ledger", 1);
      pyrite.clock.move(Duration.ofDays(31));

      assertThat(status(() -> undelete(ANNE, "projects/ledger", 2))).isEqualTo(GrpcStatus.FAILED_PRECONDITION);
    }

    @Test
    @DisplayName("an undelete needs the type's delete permission")
    void permission() throws Exception {
      pyrite.grant(ANNE, "projects/ledger", "editor", BOB);
      delete(ANNE, "projects/ledger", 1);

      assertThat(status(() -> undelete(BOB, "projects/ledger", 2))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }
  }

  @Nested
  @DisplayName("Purge")
  class Purge {

    @Test
    @DisplayName("a purge removes only what has passed its purge time")
    void due() throws Exception {
      pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "payroll", Map.of());
      delete(ANNE, "projects/ledger", 1);
      pyrite.clock.move(Duration.ofDays(10));
      delete(ANNE, "projects/payroll", 1);
      pyrite.clock.move(Duration.ofDays(21));

      assertThat(purged()).isEqualTo(1);
      assertThat(status(() -> pyrite.get(ANNE, "projects/ledger"))).isEqualTo(GrpcStatus.NOT_FOUND);
      assertThat(pyrite.get(ANNE, "projects/payroll").getFieldsMap().get("delete_time").hasTimestampValue())
          .isTrue();
      assertThat(purged()).isZero();
    }

    @Test
    @DisplayName("a purge leaves active resources alone")
    void active() throws Exception {
      pyrite.clock.move(Duration.ofDays(365));

      assertThat(purged()).isZero();
      assertThat(pyrite.get(ANNE, "projects/ledger").getName()).isEqualTo("projects/ledger");
    }

    @Test
    @DisplayName("a purged name starts again without the old grants")
    void name() throws Exception {
      pyrite.grant(ANNE, "projects/ledger", "viewer", BOB);
      delete(ANNE, "projects/ledger", 1);
      pyrite.clock.move(Duration.ofDays(31));
      purged();

      pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of());

      assertThat(pyrite.access.listGrants(ListGrantsRequest.newBuilder().setSubject(ANNE)
          .setObject("projects/ledger").build()).await(TIMEOUT).getGrantsList()).extracting(Grant::getGrantee)
          .containsExactly(ANNE);
      assertThat(status(() -> pyrite.get(BOB, "projects/ledger"))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("a purged key takes its versions with it")
    void key() throws Exception {
      pyrite.key("organizations/acme", "payments", "ENCRYPTION", "AES256_GCM");
      delete(ANNE, "keys/payments", 1);
      pyrite.clock.move(Duration.ofDays(31));

      assertThat(purged()).isEqualTo(1);
      assertThat(status(() -> pyrite.get(ANNE, "keyVersions/payments-1"))).isEqualTo(GrpcStatus.NOT_FOUND);
    }
  }
}
