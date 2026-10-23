package dev.horizon.pyrite;

import static dev.horizon.pyrite.Pyrite.ANNE;
import static dev.horizon.pyrite.Pyrite.BOB;
import static dev.horizon.pyrite.Pyrite.CAROL;
import static dev.horizon.pyrite.Pyrite.DAVE;
import static dev.horizon.pyrite.Pyrite.at;
import static dev.horizon.pyrite.Pyrite.text;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Struct;
import dev.horizon.pyrite.access.v1.CheckRequest;
import dev.horizon.pyrite.access.v1.Condition;
import dev.horizon.pyrite.access.v1.CreateGrantRequest;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.access.v1.ListSubjectsRequest;
import dev.horizon.pyrite.data.v1.MoveResourceRequest;
import dev.horizon.pyrite.data.v1.QueryResourcesRequest;
import dev.horizon.pyrite.data.v1.Value;
import dev.horizon.pyrite.schema.v1.Declaration;
import dev.horizon.pyrite.schema.v1.Type;
import io.vertx.grpc.client.InvalidStatusException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AgreementTest extends PyriteTest {

  private static final String ERIN = "user:erin";
  private static final String FRANK = "user:frank";
  private static final List<String> PRINCIPALS = List.of(ANNE, BOB, CAROL, DAVE, ERIN, FRANK, "user:nobody");
  private static final List<Struct> TIMES = List.of(at("2026-05-01T00:00:00Z"), at("2026-07-01T00:00:00Z"));

  private Declaration declaration;
  private Oracle oracle;
  private final Map<String, String> resources = new LinkedHashMap<>();
  private final Map<String, String> admins = new LinkedHashMap<>();

  @BeforeEach
  void open() throws Exception {
    this.declaration = Acme.declaration();
    try {
      this.oracle = new Oracle(pyrite.openFga, declaration);
    } catch (Exception failed) {
      throw new IllegalStateException("The oracle's model was refused", failed);
    }
    create(ANNE, "", "acme.dev/Organization", "organizations/acme", Map.of());
    create(DAVE, "", "acme.dev/Organization", "organizations/globex", Map.of());
    create(ANNE, "organizations/acme", "acme.dev/Group", "groups/finance", Map.of());
    create(ANNE, "organizations/acme", "acme.dev/Group", "groups/audit", Map.of());
    create(ANNE, "organizations/acme", "acme.dev/Project", "projects/ledger", Map.of());
    create(ANNE, "organizations/acme", "acme.dev/Project", "projects/payroll", Map.of());
    create(ANNE, "organizations/acme", "acme.dev/Project", "projects/website", Map.of());
    create(DAVE, "organizations/globex", "acme.dev/Project", "projects/mars", Map.of());
    create(ANNE, "organizations/acme", "acme.dev/Role", "roles/stock-admin", Map.of());
    create(ANNE, "organizations/acme", "acme.dev/Binding", "bindings/stock", Map.of());
    create(ANNE, "organizations/acme", "acme.dev/Binding", "bindings/ledger-stock", Map.of());
    create(ANNE, "organizations/acme", "pyrite.dev/Key", "keys/payments", Map.of("purpose", text("ENCRYPTION"),
        "algorithm", text("AES256_GCM")));

    grant(ANNE, "organizations/acme", "member", BOB);
    grant(ANNE, "groups/audit", "member", CAROL);
    grant(ANNE, "groups/finance", "member", "group:audit#member");
    grant(ANNE, "groups/finance", "member", ERIN);
    grant(ANNE, "groups/finance", "manager", FRANK);
    grant(ANNE, "projects/ledger", "viewer", "group:finance#member");
    grant(ANNE, "projects/website", "viewer", "user:*");
    grant(ANNE, "projects/website", "blocked", CAROL);
    grant(ANNE, "organizations/acme", "blocked", ERIN);
    grant(ANNE, "roles/stock-admin", "can_manage_stock", "user:*");
    grant(ANNE, "bindings/stock", "role", "role:stock-admin");
    grant(ANNE, "bindings/stock", "member", FRANK);
    grant(ANNE, "organizations/acme", "binding", "binding:stock");
    grant(ANNE, "bindings/ledger-stock", "role", "role:stock-admin");
    grant(ANNE, "bindings/ledger-stock", "member", "group:audit#member");
    grant(ANNE, "projects/ledger", "binding", "binding:ledger-stock");
    grant(ANNE, "keys/payments", "decrypter", BOB);
    grant(ANNE, "keys/payments", "encrypter", "group:finance#member");
    grant(DAVE, "organizations/globex", "member", CAROL);
    grant(DAVE, "projects/mars", "viewer", ANNE);
    grant(DAVE, "organizations/globex", "admin", ANNE);
    expiring(ANNE, "projects/payroll", "editor", BOB, "2026-06-01T00:00:00Z");
  }

  @Test
  @DisplayName("A check agrees with OpenFGA on every relation")
  void check() throws Exception {
    assertThat(checkDisagreements()).isEmpty();
  }

  @Test
  @DisplayName("A query returns exactly what OpenFGA allows")
  void query() throws Exception {
    assertThat(queryDisagreements()).isEmpty();
  }

  @Test
  @DisplayName("The subjects of a permission are exactly OpenFGA's users")
  void subjects() throws Exception {
    assertThat(subjectDisagreements()).isEmpty();
  }

  @Test
  @DisplayName("A move changes what a resource inherits, as OpenFGA does")
  void move() throws Exception {
    pyrite.data.moveResource(MoveResourceRequest.newBuilder().setSubject(ANNE).setName("projects/payroll")
        .setParent("organizations/globex").setRevision(1).build()).await(TIMEOUT);
    oracle.move("projects/payroll", "organizations/acme", "organizations/globex");

    assertThat(checkDisagreements()).isEmpty();
    assertThat(queryDisagreements()).isEmpty();
  }

  private List<String> checkDisagreements() throws Exception {
    List<String> disagreements = new ArrayList<>();
    for (Struct time : TIMES) {
      for (Map.Entry<String, String> resource : resources.entrySet()) {
        for (String relation : relations(resource.getValue())) {
          for (String principal : PRINCIPALS) {
            boolean expected = oracle.check(principal, resource.getKey(), relation, time);
            boolean actual = pyrite.access.check(CheckRequest.newBuilder().setSubject(principal)
                .setObject(resource.getKey()).setPermission(relation).setContext(time).build()).await(TIMEOUT)
                .getAllowed();
            if (actual != expected) {
              disagreements.add(principal + " " + relation + " " + resource.getKey() + " at "
                  + time.getFieldsOrThrow("current_time").getStringValue() + ": Pyrite " + actual + ", OpenFGA "
                  + expected);
            }
          }
        }
      }
    }
    return disagreements;
  }

  private List<String> queryDisagreements() throws Exception {
    List<String> disagreements = new ArrayList<>();
    Struct time = TIMES.getFirst();
    for (String type : new TreeSet<>(resources.values())) {
      for (String relation : relations(type)) {
        for (String principal : PRINCIPALS) {
          Set<String> expected = new TreeSet<>();
          for (Map.Entry<String, String> resource : resources.entrySet()) {
            if (resource.getValue().equals(type) && oracle.check(principal, resource.getKey(), relation, time)) {
              expected.add(resource.getKey());
            }
          }
          Set<String> actual;
          try {
            actual = new TreeSet<>(pyrite.query(QueryResourcesRequest.newBuilder().setSubject(principal)
                .setType(type).setPermission(relation).setPageSize(2).setContext(time).build()));
          } catch (InvalidStatusException failed) {
            disagreements.add(principal + " " + relation + " " + type + ": Pyrite fails with "
                + failed.actualStatus() + ", OpenFGA " + expected);
            continue;
          }
          if (!actual.equals(expected)) {
            disagreements.add(principal + " " + relation + " " + type + ": Pyrite " + actual + ", OpenFGA "
                + expected);
          }
        }
      }
    }
    return disagreements;
  }

  private List<String> subjectDisagreements() throws Exception {
    List<String> disagreements = new ArrayList<>();
    Struct time = TIMES.getFirst();
    for (Map.Entry<String, String> resource : resources.entrySet()) {
      for (String relation : relations(resource.getValue())) {
        Set<String> expected = oracle.users(resource.getKey(), relation, time);
        Set<String> actual = new TreeSet<>(pyrite.access.listSubjects(ListSubjectsRequest.newBuilder()
            .setSubject(admins.get(resource.getKey())).setObject(resource.getKey()).setPermission(relation)
            .setSubjectType("user").setContext(time).build()).await(TIMEOUT).getSubjectsList());
        if (!actual.equals(expected)) {
          disagreements.add(relation + " " + resource.getKey() + ": Pyrite " + actual + ", OpenFGA " + expected);
        }
      }
    }
    return disagreements;
  }

  private List<String> relations(String type) {
    Type declared = declaration.getTypesList().stream().filter(candidate -> candidate.getName().equals(type))
        .findFirst().orElseThrow();
    return new ArrayList<>(new TreeSet<>(declared.getDefinition().getRelationsMap().keySet()));
  }

  private void create(String subject, String parent, String type, String name, Map<String, Value> fields)
      throws Exception {
    try {
      pyrite.create(subject, parent, type, name.substring(name.indexOf('/') + 1), fields);
    } catch (Exception failed) {
      throw new IllegalStateException("Creating " + name + " failed", failed);
    }
    resources.put(name, type);
    admins.put(name, subject);
    if (!parent.isEmpty()) {
      oracle.parent(name, parent);
    }
    Type declared = declaration.getTypesList().stream().filter(candidate -> candidate.getName().equals(type))
        .findFirst().orElseThrow();
    if (!declared.getCreator().isEmpty()) {
      oracle.write(name, declared.getCreator(), subject, "", Struct.getDefaultInstance());
    }
  }

  private void grant(String subject, String name, String relation, String grantee) throws Exception {
    try {
      pyrite.grant(subject, name, relation, grantee);
    } catch (Exception failed) {
      throw new IllegalStateException("Pyrite refused " + relation + " on " + name + " to " + grantee, failed);
    }
    try {
      oracle.write(name, relation, grantee, "", Struct.getDefaultInstance());
    } catch (Exception failed) {
      throw new IllegalStateException("The oracle refused " + relation + " on " + name + " to " + grantee, failed);
    }
  }

  private void expiring(String subject, String name, String relation, String grantee, String until) throws Exception {
    Struct context = Struct.newBuilder().putFields("expires_at", com.google.protobuf.Value.newBuilder()
        .setStringValue(until).build()).build();
    pyrite.access.createGrant(CreateGrantRequest.newBuilder().setSubject(subject).setGrant(Grant.newBuilder()
        .setObject(name).setRelation(relation).setGrantee(grantee).setCondition(Condition.newBuilder()
            .setName("not_expired").setContext(context)))
        .build()).await(TIMEOUT);
    oracle.write(name, relation, grantee, "not_expired", context);
  }
}
