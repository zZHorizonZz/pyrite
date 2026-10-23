package dev.horizon.pyrite.data;

import static dev.horizon.pyrite.Pyrite.ANNE;
import static dev.horizon.pyrite.Pyrite.BOB;
import static dev.horizon.pyrite.Pyrite.CAROL;
import static dev.horizon.pyrite.Pyrite.DAVE;
import static dev.horizon.pyrite.Pyrite.labels;
import static dev.horizon.pyrite.Pyrite.status;
import static dev.horizon.pyrite.Pyrite.text;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.PyriteTest;
import dev.horizon.pyrite.data.v1.DeleteResourceRequest;
import dev.horizon.pyrite.data.v1.QueryResourcesRequest;
import dev.horizon.pyrite.data.v1.QueryResourcesResponse;
import io.vertx.grpc.common.GrpcStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QueryResourcesTest extends PyriteTest {

  @BeforeEach
  void open() throws Exception {
    pyrite.create(ANNE, "", "acme.dev/Organization", "acme", Map.of());
    pyrite.create(DAVE, "", "acme.dev/Organization", "globex", Map.of());
    pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of("labels", labels("env", "prod"),
        "display_name", text("General ledger")));
    pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "payroll", Map.of("labels", labels("env", "test"),
        "display_name", text("Payroll")));
    pyrite.create(ANNE, "organizations/acme", "acme.dev/Project", "website", Map.of("labels", labels("env", "prod"),
        "display_name", text("Website")));
    pyrite.create(DAVE, "organizations/globex", "acme.dev/Project", "mars", Map.of("labels", labels("env", "prod")));
  }

  private QueryResourcesRequest.Builder request(String subject) {
    return QueryResourcesRequest.newBuilder().setSubject(subject).setType("acme.dev/Project").setOrderBy("name");
  }

  @Nested
  @DisplayName("Permissions")
  class Permissions {

    @Test
    @DisplayName("a query returns only what the subject may read")
    void read() throws Exception {
      pyrite.grant(ANNE, "organizations/acme", "member", BOB);
      pyrite.grant(DAVE, "projects/mars", "viewer", CAROL);

      assertThat(pyrite.query(request(BOB).build())).containsExactly("projects/ledger", "projects/payroll",
          "projects/website");
      assertThat(pyrite.query(request(CAROL).build())).containsExactly("projects/mars");
      assertThat(pyrite.query(request(DAVE).build())).containsExactly("projects/mars");
      assertThat(pyrite.query(request("user:nobody").build())).isEmpty();
    }

    @Test
    @DisplayName("a query checks the permission that it names")
    void named() throws Exception {
      pyrite.grant(ANNE, "organizations/acme", "member", BOB);
      pyrite.grant(ANNE, "projects/payroll", "editor", BOB);

      assertThat(pyrite.query(request(BOB).setPermission("can_edit").build())).containsExactly("projects/payroll");
      assertThat(pyrite.query(request(ANNE).setPermission("owner").build())).containsExactly("projects/ledger",
          "projects/payroll", "projects/website");
    }

    @Test
    @DisplayName("a block leaves the blocked resources out")
    void blocked() throws Exception {
      pyrite.grant(ANNE, "organizations/acme", "member", BOB);
      pyrite.grant(ANNE, "projects/website", "blocked", BOB);

      assertThat(pyrite.query(request(BOB).setPermission("can_view").build())).containsExactly("projects/ledger",
          "projects/payroll");
    }

    @Test
    @DisplayName("a permission that the type does not declare is refused")
    void undeclared() {
      assertThat(status(() -> pyrite.query(request(BOB).setPermission("can_fly").build())))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("a type that is not declared is refused")
    void undeclaredType() {
      assertThat(status(() -> pyrite.query(request(ANNE).setType("acme.dev/Folder").build())))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }

  @Nested
  @DisplayName("Filter")
  class Filter {

    @Test
    @DisplayName("a filter narrows the results")
    void narrows() throws Exception {
      assertThat(pyrite.query(request(ANNE).setFilter("labels.env = \"prod\"").build()))
          .containsExactly("projects/ledger", "projects/website");
      assertThat(pyrite.query(request(ANNE).setFilter("display_name = \"Payroll\"").build()))
          .containsExactly("projects/payroll");
      assertThat(pyrite.query(request(ANNE).setFilter("labels.env = \"prod\" AND display_name = \"Website\"")
          .build())).containsExactly("projects/website");
      assertThat(pyrite.query(request(ANNE).setFilter("labels.env = \"staging\"").build())).isEmpty();
    }

    @Test
    @DisplayName("a filter never widens what the subject may read")
    void permission() throws Exception {
      assertThat(pyrite.query(request(ANNE).setFilter("labels.env = \"prod\" OR labels.env != \"prod\"").build()))
          .containsExactly("projects/ledger", "projects/payroll", "projects/website");
    }

    @ParameterizedTest
    @ValueSource(strings = { "labels.env = ", "labels.env = \"prod", "AND", "name = (\"x\"" })
    @DisplayName("a filter that does not parse is refused")
    void syntax(String filter) {
      assertThat(status(() -> pyrite.query(request(ANNE).setFilter(filter).build())))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("a filter on a field that the type does not declare is refused")
    void unknownField() {
      assertThat(status(() -> pyrite.query(request(ANNE).setFilter("budget > 3").build())))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }

  @Nested
  @DisplayName("Order")
  class Order {

    @Test
    @DisplayName("an order sorts the results")
    void sorts() throws Exception {
      assertThat(pyrite.query(request(ANNE).setOrderBy("name desc").build())).containsExactly("projects/website",
          "projects/payroll", "projects/ledger");
      assertThat(pyrite.query(request(ANNE).setOrderBy("display_name").build())).containsExactly(
          "projects/ledger", "projects/payroll", "projects/website");
    }

    @Test
    @DisplayName("an order on a field that the type does not declare is refused")
    void unknownField() {
      assertThat(status(() -> pyrite.query(request(ANNE).setOrderBy("budget").build())))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }

  @Nested
  @DisplayName("Pages")
  class Pages {

    @Test
    @DisplayName("pages cover every resource once, in order")
    void cover() throws Exception {
      List<String> names = new ArrayList<>();
      String token = "";
      do {
        QueryResourcesResponse page = pyrite.data.queryResources(request(ANNE).setPageSize(1).setPageToken(token)
            .build()).await(TIMEOUT);
        assertThat(page.getResourcesCount()).isLessThanOrEqualTo(1);
        page.getResourcesList().forEach(resource -> names.add(resource.getName()));
        token = page.getNextPageToken();
      } while (!token.isEmpty());

      assertThat(names).containsExactly("projects/ledger", "projects/payroll", "projects/website");
    }

    @Test
    @DisplayName("a page token works only for the query that gave it")
    void otherQuery() throws Exception {
      String token = pyrite.data.queryResources(request(ANNE).setPageSize(1).build()).await(TIMEOUT)
          .getNextPageToken();

      assertThat(status(() -> pyrite.data.queryResources(request(ANNE).setFilter("labels.env = \"prod\"")
          .setPageSize(1).setPageToken(token).build()).await(TIMEOUT))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      assertThat(pyrite.data.queryResources(request(ANNE).setPageSize(1).setPageToken(token).build())
          .await(TIMEOUT).getResources(0).getName()).isEqualTo("projects/payroll");
    }

    @ParameterizedTest
    @ValueSource(strings = { "not-a-token", "AAAA", "eyJ9" })
    @DisplayName("a page token that Pyrite did not make is refused")
    void forged(String token) {
      assertThat(status(() -> pyrite.data.queryResources(request(ANNE).setPageToken(token).build())
          .await(TIMEOUT))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("a page size above the largest is cut to the largest")
    void largest() throws Exception {
      assertThat(pyrite.data.queryResources(request(ANNE).setPageSize(100_000).build()).await(TIMEOUT)
          .getResourcesCount()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("Deleted resources")
  class Deleted {

    @Test
    @DisplayName("deleted resources are left out unless the query asks for them")
    void shown() throws Exception {
      pyrite.data.deleteResource(DeleteResourceRequest.newBuilder().setSubject(ANNE).setName("projects/payroll")
          .setRevision(1).build()).await(TIMEOUT);

      assertThat(pyrite.query(request(ANNE).build())).containsExactly("projects/ledger", "projects/website");
      assertThat(pyrite.query(request(ANNE).setShowDeleted(true).build())).containsExactly("projects/ledger",
          "projects/payroll", "projects/website");
    }
  }
}
