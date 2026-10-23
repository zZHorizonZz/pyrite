package dev.horizon.pyrite;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.access.v1.CheckRequest;
import dev.horizon.pyrite.access.v1.CreateGrantRequest;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.data.v1.CreateResourceRequest;
import dev.horizon.pyrite.data.v1.QueryResourcesRequest;
import dev.horizon.pyrite.data.v1.Resource;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BindingTest {

  private static final String ANNE = "user:anne";
  private static final String BOB = "user:bob";
  private static final String CAROL = "user:carol";

  private Installation installation;

  @BeforeEach
  void start() throws Exception {
    installation = Installation.start(Instant.parse("2026-01-01T00:00:00Z"));
    create("", "acme.dev/Organization", "acme");
    create("organizations/acme", "acme.dev/Project", "ledger");
    create("organizations/acme", "acme.dev/Role", "stock-admin");
    grant("roles/stock-admin", "can_manage_stock", "user:*");
  }

  @AfterEach
  void stop() throws Exception {
    installation.close();
  }

  @Test
  void aBindingOnTheOrganizationReachesItsProjects() throws Exception {
    create("organizations/acme", "acme.dev/Binding", "stock");
    grant("bindings/stock", "role", "role:stock-admin");
    grant("bindings/stock", "member", BOB);
    grant("organizations/acme", "binding", "binding:stock");

    assertThat(check(BOB, "projects/ledger")).isTrue();
    assertThat(check(BOB, "organizations/acme")).isTrue();
    assertThat(check(CAROL, "projects/ledger")).isFalse();
    assertThat(query(BOB)).containsExactly("projects/ledger");
  }

  @Test
  void aBindingOnAProjectStaysOnIt() throws Exception {
    create("organizations/acme", "acme.dev/Binding", "ledger-stock");
    grant("bindings/ledger-stock", "role", "role:stock-admin");
    grant("bindings/ledger-stock", "member", CAROL);
    grant("projects/ledger", "binding", "binding:ledger-stock");

    assertThat(check(CAROL, "projects/ledger")).isTrue();
    assertThat(check(CAROL, "organizations/acme")).isFalse();
  }

  private void create(String parent, String type, String id) throws Exception {
    installation.data.createResource(CreateResourceRequest.newBuilder().setSubject(ANNE).setParent(parent)
        .setResourceId(id).setResource(Resource.newBuilder().setType(type)).build()).await(10, TimeUnit.SECONDS);
  }

  private void grant(String object, String relation, String grantee) throws Exception {
    installation.access.createGrant(CreateGrantRequest.newBuilder().setSubject(ANNE).setGrant(Grant.newBuilder()
        .setObject(object).setRelation(relation).setGrantee(grantee)).build()).await(10, TimeUnit.SECONDS);
  }

  private boolean check(String principal, String object) throws Exception {
    return installation.access.check(CheckRequest.newBuilder().setSubject(ANNE).setPrincipal(principal)
        .setObject(object).setPermission("can_manage_stock").build()).await(10, TimeUnit.SECONDS).getAllowed();
  }

  private List<String> query(String subject) throws Exception {
    return installation.data.queryResources(QueryResourcesRequest.newBuilder().setSubject(subject)
        .setType("acme.dev/Project").setPermission("can_manage_stock").build()).await(10, TimeUnit.SECONDS)
        .getResourcesList().stream().map(Resource::getName).toList();
  }
}
