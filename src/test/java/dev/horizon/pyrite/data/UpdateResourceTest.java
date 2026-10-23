package dev.horizon.pyrite.data;

import static dev.horizon.pyrite.Pyrite.ANNE;
import static dev.horizon.pyrite.Pyrite.BOB;
import static dev.horizon.pyrite.Pyrite.status;
import static dev.horizon.pyrite.Pyrite.text;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.FieldMask;
import com.google.protobuf.NullValue;
import dev.horizon.pyrite.PyriteTest;
import dev.horizon.pyrite.data.v1.DeleteResourceRequest;
import dev.horizon.pyrite.data.v1.Resource;
import dev.horizon.pyrite.data.v1.UpdateResourceRequest;
import dev.horizon.pyrite.data.v1.Value;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UpdateResourceTest extends PyriteTest {

  private Resource ledger;

  @BeforeEach
  void open() throws Exception {
    pyrite.create(ANNE, "", "acme.dev/Organization", "acme", Map.of());
    ledger = pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of("display_name",
        text("Ledger"), "billing_account", text("b-1")));
  }

  private Resource update(String subject, String name, String field, Value value, long revision) throws Exception {
    return pyrite.data.updateResource(UpdateResourceRequest.newBuilder().setSubject(subject)
        .setResource(Resource.newBuilder().setName(name).putFields(field, value))
        .setUpdateMask(FieldMask.newBuilder().addPaths(field)).setRevision(revision).build()).await(TIMEOUT);
  }

  @Nested
  @DisplayName("Mask")
  class Mask {

    @Test
    @DisplayName("an update sets the masked fields, the next revision and the update time")
    void sets() throws Exception {
      pyrite.clock.move(Duration.ofMinutes(5));

      Resource renamed = update(ANNE, "projects/ledger", "display_name", text("General ledger"), 1);

      assertThat(renamed.getFieldsMap().get("display_name").getStringValue()).isEqualTo("General ledger");
      assertThat(renamed.getFieldsMap().get("revision").getIntegerValue()).isEqualTo(2);
      assertThat(renamed.getFieldsMap().get("update_time").getTimestampValue().getSeconds())
          .isEqualTo(ledger.getFieldsMap().get("update_time").getTimestampValue().getSeconds() + 300);
      assertThat(renamed.getFieldsMap().get("create_time")).isEqualTo(ledger.getFieldsMap().get("create_time"));
    }

    @Test
    @DisplayName("a field outside the mask stays, whatever the request holds")
    void outside() throws Exception {
      Resource updated = pyrite.data.updateResource(UpdateResourceRequest.newBuilder().setSubject(ANNE)
          .setResource(Resource.newBuilder().setName("projects/ledger").putFields("display_name", text("Other"))
              .putFields("billing_account", text("b-2")))
          .setUpdateMask(FieldMask.newBuilder().addPaths("billing_account")).setRevision(1).build())
          .await(TIMEOUT);

      assertThat(updated.getFieldsMap().get("display_name").getStringValue()).isEqualTo("Ledger");
      assertThat(updated.getFieldsMap().get("billing_account").getStringValue()).isEqualTo("b-2");
    }

    @Test
    @DisplayName("a null unsets a field")
    void unset() throws Exception {
      Resource cleared = update(ANNE, "projects/ledger", "billing_account", Value.newBuilder()
          .setNullValue(NullValue.NULL_VALUE).build(), 1);

      assertThat(cleared.getFieldsMap().get("billing_account").hasNullValue()).isTrue();
    }

    @Test
    @DisplayName("an empty mask is refused")
    void empty() {
      assertThat(status(() -> pyrite.data.updateResource(UpdateResourceRequest.newBuilder().setSubject(ANNE)
          .setResource(Resource.newBuilder().setName("projects/ledger")).setRevision(1).build()).await(TIMEOUT)))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }

  @Nested
  @DisplayName("Fields")
  class Fields {

    @ParameterizedTest
    @ValueSource(strings = { "name", "uid", "parent", "revision", "create_time", "update_time", "delete_time",
        "purge_time" })
    @DisplayName("an update sets no field of Pyrite's")
    void pyrites(String field) {
      assertThat(status(() -> update(ANNE, "projects/ledger", field, text("x"), 1)))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("a field fixed at creation does not change")
    void fixed() throws Exception {
      pyrite.key("organizations/acme", "payments", "ENCRYPTION", "AES256_GCM");

      assertThat(status(() -> update(ANNE, "keys/payments", "purpose", text("SIGNING"), 1)))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      assertThat(status(() -> update(ANNE, "keys/payments", "algorithm", text("ES256"), 1)))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("a field that the type does not declare, or a value of another kind, is refused")
    void unknown() {
      assertThat(status(() -> update(ANNE, "projects/ledger", "budget", text("7"), 1)))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      assertThat(status(() -> update(ANNE, "projects/ledger", "display_name", Value.newBuilder().setIntegerValue(7)
          .build(), 1))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }

  @Nested
  @DisplayName("Revisions")
  class Revisions {

    @Test
    @DisplayName("an update at another revision is aborted and changes nothing")
    void stale() throws Exception {
      update(ANNE, "projects/ledger", "display_name", text("General"), 1);

      assertThat(status(() -> update(ANNE, "projects/ledger", "display_name", text("Old"), 1)))
          .isEqualTo(GrpcStatus.ABORTED);
      assertThat(status(() -> update(ANNE, "projects/ledger", "display_name", text("Future"), 7)))
          .isEqualTo(GrpcStatus.ABORTED);
      assertThat(pyrite.get(ANNE, "projects/ledger").getFieldsMap().get("display_name").getStringValue())
          .isEqualTo("General");
    }
  }

  @Nested
  @DisplayName("Permissions")
  class Permissions {

    @Test
    @DisplayName("the subject needs the type's update permission")
    void update() throws Exception {
      pyrite.grant(ANNE, "projects/ledger", "viewer", BOB);

      assertThat(status(() -> UpdateResourceTest.this.update(BOB, "projects/ledger", "display_name", text("Mine"),
          1))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
      pyrite.grant(ANNE, "projects/ledger", "editor", BOB);
      assertThat(UpdateResourceTest.this.update(BOB, "projects/ledger", "display_name", text("Mine"), 1)
          .getFieldsMap().get("revision").getIntegerValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("a deleted resource does not change")
    void deleted() throws Exception {
      pyrite.data.deleteResource(DeleteResourceRequest.newBuilder().setSubject(ANNE).setName("projects/ledger")
          .setRevision(1).build()).await(TIMEOUT);

      assertThat(status(() -> UpdateResourceTest.this.update(ANNE, "projects/ledger", "display_name", text("Gone"),
          2))).isEqualTo(GrpcStatus.FAILED_PRECONDITION);
    }

    @Test
    @DisplayName("a missing resource is not found")
    void missing() {
      assertThat(status(() -> UpdateResourceTest.this.update(ANNE, "projects/payroll", "display_name", text("X"),
          1))).isEqualTo(GrpcStatus.NOT_FOUND);
    }
  }
}
