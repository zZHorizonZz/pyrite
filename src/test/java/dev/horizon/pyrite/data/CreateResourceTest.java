package dev.horizon.pyrite.data;

import static dev.horizon.pyrite.Pyrite.ANNE;
import static dev.horizon.pyrite.Pyrite.BOB;
import static dev.horizon.pyrite.Pyrite.START;
import static dev.horizon.pyrite.Pyrite.integer;
import static dev.horizon.pyrite.Pyrite.labels;
import static dev.horizon.pyrite.Pyrite.status;
import static dev.horizon.pyrite.Pyrite.text;
import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.PyriteTest;
import dev.horizon.pyrite.data.v1.MapValue;
import dev.horizon.pyrite.data.v1.Resource;
import dev.horizon.pyrite.data.v1.Value;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CreateResourceTest extends PyriteTest {

  @BeforeEach
  void open() throws Exception {
    pyrite.create(ANNE, "", "acme.dev/Organization", "acme", Map.of());
  }

  @Nested
  @DisplayName("Fields")
  class Fields {

    @Test
    @DisplayName("a resource gets its name and the standard fields")
    void standard() throws Exception {
      Resource ledger = pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of(
          "display_name", text("Ledger"), "labels", labels("tier", "gold"), "billing_account", text("b-1")));

      Map<String, Value> fields = ledger.getFieldsMap();
      assertThat(ledger.getName()).isEqualTo("projects/ledger");
      assertThat(ledger.getType()).isEqualTo("acme.dev/Project");
      assertThat(fields.get("display_name").getStringValue()).isEqualTo("Ledger");
      assertThat(fields.get("labels").getMapValue().getFieldsMap().get("tier").getStringValue()).isEqualTo("gold");
      assertThat(fields.get("billing_account").getStringValue()).isEqualTo("b-1");
      assertThat(fields.get("parent").getStringValue()).isEqualTo("organizations/acme");
      assertThat(fields.get("revision").getIntegerValue()).isEqualTo(1);
      assertThat(fields.get("uid").getStringValue()).hasSize(36);
      assertThat(fields.get("create_time").getTimestampValue().getSeconds()).isEqualTo(START.getEpochSecond());
      assertThat(fields.get("update_time")).isEqualTo(fields.get("create_time"));
      assertThat(fields.get("delete_time").hasNullValue()).isTrue();
      assertThat(fields.get("purge_time").hasNullValue()).isTrue();
    }

    @Test
    @DisplayName("every resource gets its own uid")
    void uid() throws Exception {
      String ledger = pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of())
          .getFieldsMap().get("uid").getStringValue();
      String payroll = pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "payroll", Map.of())
          .getFieldsMap().get("uid").getStringValue();

      assertThat(ledger).isNotEqualTo(payroll);
    }

    @ParameterizedTest
    @ValueSource(strings = { "name", "uid", "parent", "revision", "create_time", "update_time", "delete_time",
        "purge_time" })
    @DisplayName("a request sets no field of Pyrite's")
    void pyrites(String field) {
      assertThat(status(() -> pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of(field,
          text("x"))))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("a field takes only values of its kind")
    void kinds() {
      assertThat(status(() -> pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of(
          "billing_account", integer(7))))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      assertThat(status(() -> pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of(
          "display_name", Value.newBuilder().setBoolValue(true).build())))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("a label is a string")
    void labelValues() {
      Value numbered = Value.newBuilder().setMapValue(MapValue.newBuilder().putFields("rank", integer(1))).build();

      assertThat(status(() -> pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of(
          "labels", numbered)))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("a field that the type does not declare is refused")
    void undeclared() {
      assertThat(status(() -> pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of(
          "budget", integer(7))))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }

  @Nested
  @DisplayName("Names")
  class Names {

    @ParameterizedTest
    @ValueSource(strings = { "Ledger", "1ledger", "ledger-", "led_ger", "led ger", "led/ger", "" })
    @DisplayName("an id is lowercase letters, digits and hyphens, and starts with a letter")
    void invalid(String id) {
      assertThat(status(() -> pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", id, Map.of())))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("an id has at most 63 characters")
    void length() throws Exception {
      String longest = "a" + "-b".repeat(31);

      assertThat(pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", longest, Map.of()).getName())
          .isEqualTo("projects/" + longest);
      assertThat(status(() -> pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", longest + "c",
          Map.of()))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("a name is taken once across the whole type")
    void taken() throws Exception {
      pyrite.create(ANNE, "", "acme.dev/Organization", "globex", Map.of());
      pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of());

      assertThat(status(() -> pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of())))
          .isEqualTo(GrpcStatus.ALREADY_EXISTS);
      assertThat(status(() -> pyrite.create(ANNE, "organizations/globex", "acme.dev/Project", "ledger",
          Map.of()))).isEqualTo(GrpcStatus.ALREADY_EXISTS);
    }

    @Test
    @DisplayName("a type that is not declared is refused")
    void undeclaredType() {
      assertThat(status(() -> pyrite.create(ANNE, "", "acme.dev/Folder", "docs", Map.of())))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }

  @Nested
  @DisplayName("Parents")
  class Parents {

    @Test
    @DisplayName("a missing parent is not found")
    void missing() {
      assertThat(status(() -> pyrite.create(ANNE, "organizations/globex", "acme.dev/Project", "ledger", Map.of())))
          .isEqualTo(GrpcStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("a parent is of a parent type of the resource")
    void type() throws Exception {
      pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of());

      assertThat(status(() -> pyrite.create(ANNE, "projects/ledger", "acme.dev/Project", "mars", Map.of())))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("a type with parents needs one, and a type without parents takes none")
    void presence() {
      assertThat(status(() -> pyrite.create(ANNE, "", "acme.dev/Project", "ledger", Map.of())))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      assertThat(status(() -> pyrite.create(ANNE, "organizations/acme", "acme.dev/Organization", "globex",
          Map.of()))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }

  @Nested
  @DisplayName("Permissions")
  class Permissions {

    @Test
    @DisplayName("the creator is granted the type's creator relation")
    void creator() throws Exception {
      pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of());

      assertThat(pyrite.check(ANNE, "projects/ledger", "owner", com.google.protobuf.Struct.getDefaultInstance()))
          .isTrue();
      assertThat(status(() -> pyrite.get(BOB, "organizations/acme"))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("creating under a parent needs the create permission there")
    void create() throws Exception {
      assertThat(status(() -> pyrite.create(BOB, "organizations/acme", "acme.dev/Project", "ledger", Map.of())))
          .isEqualTo(GrpcStatus.PERMISSION_DENIED);
      pyrite.grant(ANNE, "organizations/acme", "member", BOB);
      assertThat(status(() -> pyrite.create(BOB, "organizations/acme", "acme.dev/Project", "ledger", Map.of())))
          .isEqualTo(GrpcStatus.PERMISSION_DENIED);
      pyrite.grant(ANNE, "organizations/acme", "admin", BOB);
      assertThat(pyrite.create(BOB, "organizations/acme", "acme.dev/Project", "ledger", Map.of()).getName())
          .isEqualTo("projects/ledger");
    }

    @Test
    @DisplayName("a type without parents is created by anyone")
    void topLevel() throws Exception {
      assertThat(pyrite.create(BOB, "", "acme.dev/Organization", "globex", Map.of()).getName())
          .isEqualTo("organizations/globex");
    }

    @ParameterizedTest
    @ValueSource(strings = { "bob", "accounts/bob", "user:", ":bob", "" })
    @DisplayName("the subject is a principal")
    void subject(String subject) {
      assertThat(status(() -> pyrite.create(subject, "organizations/acme", "acme.dev/Project", "ledger", Map.of())))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }
}
