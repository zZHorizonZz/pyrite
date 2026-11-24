package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;
import static dev.horizon.pyrite.PyriteFixture.CAROL;
import static dev.horizon.pyrite.PyriteFixture.DAVE;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import com.google.protobuf.Struct;
import dev.horizon.pyrite.Acme;
import dev.horizon.pyrite.Oracle;
import dev.horizon.pyrite.access.v1.CreateGrantRequest;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.crypto.v1.Algorithm;
import dev.horizon.pyrite.crypto.v1.CreateKeyRequest;
import dev.horizon.pyrite.schema.v1.Declaration;
import dev.horizon.pyrite.schema.v1.Type;
import java.util.LinkedHashMap;

/**
 * Builds the same resources and grants in Pyrite and in OpenFGA with the model of the declaration: organizations,
 * groups in groups, projects, roles bound to members, a key, blocks, wildcards, and a grant that expires.
 */
public record BuildAgreementWorld() implements Condition {

  private static final String ERIN = "pyrite.dev/Account:accounts/erin";
  private static final String FRANK = "pyrite.dev/Account:accounts/frank";

  @Override
  public void evaluate(Environment environment) throws Exception {
    Declaration declaration = Acme.declaration();
    AgreementWorld world = new AgreementWorld(new Oracle(environment.pyrite().openFga, declaration), declaration,
        new LinkedHashMap<>(), new LinkedHashMap<>());
    environment.put(Value.WORLD, world);
    create(environment, world, ANNE, "", "acme.dev/Organization", "acme.dev/Organization:organizations/acme");
    create(environment, world, DAVE, "", "acme.dev/Organization", "acme.dev/Organization:organizations/globex");
    create(environment, world, ANNE, "acme.dev/Organization:organizations/acme", "acme.dev/Group",
        "acme.dev/Group:groups/finance");
    create(environment, world, ANNE, "acme.dev/Organization:organizations/acme", "acme.dev/Group",
        "acme.dev/Group:groups/audit");
    create(environment, world, ANNE, "acme.dev/Organization:organizations/acme", "acme.dev/Project",
        "acme.dev/Project:projects/ledger");
    create(environment, world, ANNE, "acme.dev/Organization:organizations/acme", "acme.dev/Project",
        "acme.dev/Project:projects/payroll");
    create(environment, world, ANNE, "acme.dev/Organization:organizations/acme", "acme.dev/Project",
        "acme.dev/Project:projects/website");
    create(environment, world, DAVE, "acme.dev/Organization:organizations/globex", "acme.dev/Project",
        "acme.dev/Project:projects/mars");
    create(environment, world, ANNE, "acme.dev/Organization:organizations/acme", "acme.dev/Role",
        "acme.dev/Role:roles/stock-admin");
    create(environment, world, ANNE, "acme.dev/Organization:organizations/acme", "acme.dev/Binding",
        "acme.dev/Binding:bindings/stock");
    create(environment, world, ANNE, "acme.dev/Organization:organizations/acme", "acme.dev/Binding",
        "acme.dev/Binding:bindings/ledger-stock");
    create(environment, world, ANNE, "acme.dev/Organization:organizations/acme", "pyrite.dev/Key",
        "pyrite.dev/Key:keys/payments");
    grant(environment, world, ANNE, "acme.dev/Organization:organizations/acme", "member", BOB);
    grant(environment, world, ANNE, "acme.dev/Group:groups/audit", "member", CAROL);
    grant(environment, world, ANNE, "acme.dev/Group:groups/finance", "member", "acme.dev/Group:groups/audit#member");
    grant(environment, world, ANNE, "acme.dev/Group:groups/finance", "member", ERIN);
    grant(environment, world, ANNE, "acme.dev/Group:groups/finance", "manager", FRANK);
    grant(environment, world, ANNE, "acme.dev/Project:projects/ledger", "viewer", "acme.dev/Group:groups/finance#member");
    grant(environment, world, ANNE, "acme.dev/Project:projects/website", "viewer", "pyrite.dev/Account:*");
    grant(environment, world, ANNE, "acme.dev/Project:projects/website", "blocked", CAROL);
    grant(environment, world, ANNE, "acme.dev/Organization:organizations/acme", "blocked", ERIN);
    grant(environment, world, ANNE, "acme.dev/Role:roles/stock-admin", "can_manage_stock", "pyrite.dev/Account:*");
    grant(environment, world, ANNE, "acme.dev/Binding:bindings/stock", "role", "acme.dev/Role:roles/stock-admin");
    grant(environment, world, ANNE, "acme.dev/Binding:bindings/stock", "member", FRANK);
    grant(environment, world, ANNE, "acme.dev/Organization:organizations/acme", "binding", "acme.dev/Binding:bindings/stock");
    grant(environment, world, ANNE, "acme.dev/Binding:bindings/ledger-stock", "role", "acme.dev/Role:roles/stock-admin");
    grant(environment, world, ANNE, "acme.dev/Binding:bindings/ledger-stock", "member", "acme.dev/Group:groups/audit#member");
    grant(environment, world, ANNE, "acme.dev/Project:projects/ledger", "binding", "acme.dev/Binding:bindings/ledger-stock");
    grant(environment, world, ANNE, "pyrite.dev/Key:keys/payments", "decrypter", BOB);
    grant(environment, world, ANNE, "pyrite.dev/Key:keys/payments", "encrypter", "acme.dev/Group:groups/finance#member");
    grant(environment, world, DAVE, "acme.dev/Organization:organizations/globex", "member", CAROL);
    grant(environment, world, DAVE, "acme.dev/Project:projects/mars", "viewer", ANNE);
    grant(environment, world, DAVE, "acme.dev/Organization:organizations/globex", "admin", ANNE);
    Struct until = Struct.newBuilder().putFields("expires_at", com.google.protobuf.Value.newBuilder()
        .setStringValue("2026-06-01T00:00:00Z").build()).build();
    environment.pyrite().access.createGrant(CreateGrantRequest.newBuilder().setSubject(ANNE).setGrant(Grant
        .newBuilder().setObject("acme.dev/Project:projects/payroll").setRelation("editor").setGrantee(BOB).setCondition(
            dev.horizon.pyrite.access.v1.Condition.newBuilder().setName("not_expired").setContext(until)))
        .build()).await(TIMEOUT);
    world.oracle().write("acme.dev/Project:projects/payroll", "editor", BOB, "not_expired", until);
  }

  private static void create(Environment environment, AgreementWorld world, String subject, String parent,
      String type, String name) throws Exception {
    if (type.equals("pyrite.dev/Key")) {
      environment.pyrite().cryptography.createKey(CreateKeyRequest.newBuilder().setSubject(subject)
          .setKeyId(name.substring(name.lastIndexOf('/') + 1)).setParent(parent).setAlgorithm(Algorithm.AES256_GCM)
          .build()).await(TIMEOUT);
    } else {
      new CreateObject(subject, name, parent).evaluate(environment);
    }
    world.resources().put(name, type);
    world.admins().put(name, subject);
    if (!parent.isEmpty()) {
      world.oracle().parent(name, parent);
    }
    Type declared = world.declaration().getTypesList().stream().filter(candidate -> candidate.getName()
        .equals(type)).findFirst().orElseThrow();
    if (!declared.getCreator().isEmpty()) {
      world.oracle().write(name, declared.getCreator(), subject, "", Struct.getDefaultInstance());
    }
  }

  private static void grant(Environment environment, AgreementWorld world, String subject, String name,
      String relation, String grantee) throws Exception {
    new GrantRelation(subject, name, relation, grantee).evaluate(environment);
    world.oracle().write(name, relation, grantee, "", Struct.getDefaultInstance());
  }
}
