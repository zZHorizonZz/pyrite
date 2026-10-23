package dev.horizon.pyrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.FieldMask;
import com.google.protobuf.Struct;
import dev.horizon.pyrite.access.v1.AccessGrpcClient;
import dev.horizon.pyrite.access.v1.Change;
import dev.horizon.pyrite.access.v1.CheckRequest;
import dev.horizon.pyrite.access.v1.Condition;
import dev.horizon.pyrite.access.v1.CreateGrantRequest;
import dev.horizon.pyrite.access.v1.DeleteGrantRequest;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.access.v1.ListChangesRequest;
import dev.horizon.pyrite.access.v1.ListGrantsRequest;
import dev.horizon.pyrite.access.v1.ListSubjectsRequest;
import dev.horizon.pyrite.data.v1.CreateResourceRequest;
import dev.horizon.pyrite.data.v1.DataGrpcClient;
import dev.horizon.pyrite.data.v1.DeleteResourceRequest;
import dev.horizon.pyrite.data.v1.GetResourceRequest;
import dev.horizon.pyrite.data.v1.MapValue;
import dev.horizon.pyrite.data.v1.MoveResourceRequest;
import dev.horizon.pyrite.data.v1.PurgeResourcesRequest;
import dev.horizon.pyrite.data.v1.QueryResourcesRequest;
import dev.horizon.pyrite.data.v1.Resource;
import dev.horizon.pyrite.data.v1.UndeleteResourceRequest;
import dev.horizon.pyrite.data.v1.UpdateResourceRequest;
import dev.horizon.pyrite.data.v1.Value;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PyriteTest {

  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
  private static final String ANNE = "user:anne";
  private static final String BOB = "user:bob";
  private static final String CAROL = "user:carol";
  private static final String DAVE = "user:dave";

  private Installation installation;
  private MovingClock clock;
  private DataGrpcClient data;
  private AccessGrpcClient access;

  @BeforeEach
  void start() throws Exception {
    installation = Installation.start(START);
    clock = installation.clock;
    data = installation.data;
    access = installation.access;
  }

  @AfterEach
  void stop() throws Exception {
    installation.close();
  }

  @Test
  void theCreatorOfAnOrganizationAdministersIt() throws Exception {
    Resource acme = create(ANNE, "", "acme.dev/Organization", "acme", Map.of("display_name", text("Acme")));

    assertThat(acme.getName()).isEqualTo("organizations/acme");
    assertThat(acme.getFieldsMap().get("display_name").getStringValue()).isEqualTo("Acme");
    assertThat(acme.getFieldsMap().get("revision").getIntegerValue()).isEqualTo(1);
    assertThat(acme.getFieldsMap().get("create_time").getTimestampValue().getSeconds())
        .isEqualTo(START.getEpochSecond());
    assertThat(get(ANNE, "organizations/acme").getFieldsMap().get("uid").getStringValue()).isNotEmpty();
    assertThat(status(() -> get(BOB, "organizations/acme"))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    assertThat(status(() -> get(ANNE, "organizations/globex"))).isEqualTo(GrpcStatus.NOT_FOUND);
    assertThat(status(() -> create(ANNE, "", "acme.dev/Organization", "acme", Map.of())))
        .isEqualTo(GrpcStatus.ALREADY_EXISTS);
  }

  @Test
  void aProjectInheritsTheAccessGrantedOnItsOrganization() throws Exception {
    create(ANNE, "", "acme.dev/Organization", "acme", Map.of());
    create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of("billing_account", text("b-1")));
    assertThat(status(() -> get(BOB, "projects/ledger"))).isEqualTo(GrpcStatus.PERMISSION_DENIED);

    grant(ANNE, "organizations/acme", "member", BOB);

    assertThat(get(BOB, "projects/ledger").getFieldsMap().get("billing_account").getStringValue()).isEqualTo("b-1");
    assertThat(status(() -> create(BOB, "organizations/acme", "acme.dev/Project", "payroll", Map.of())))
        .isEqualTo(GrpcStatus.PERMISSION_DENIED);
    assertThat(check(ANNE, BOB, "projects/ledger", "can_edit")).isFalse();
    assertThat(check(ANNE, ANNE, "projects/ledger", "can_edit")).isTrue();
  }

  @Test
  void aQueryReturnsOnlyWhatTheSubjectMayView() throws Exception {
    create(ANNE, "", "acme.dev/Organization", "acme", Map.of());
    create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of("labels", labels("env", "prod")));
    create(ANNE, "organizations/acme", "acme.dev/Project", "payroll", Map.of("labels", labels("env", "test")));
    create(DAVE, "", "acme.dev/Organization", "globex", Map.of());
    create(DAVE, "organizations/globex", "acme.dev/Project", "mars", Map.of("labels", labels("env", "prod")));
    grant(ANNE, "organizations/acme", "member", BOB);
    grant(DAVE, "projects/mars", "viewer", CAROL);

    assertThat(query(BOB, "acme.dev/Project", "")).containsExactly("projects/ledger", "projects/payroll");
    assertThat(query(BOB, "acme.dev/Project", "labels.env = \"prod\"")).containsExactly("projects/ledger");
    assertThat(query(CAROL, "acme.dev/Project", "")).containsExactly("projects/mars");
    assertThat(query(DAVE, "acme.dev/Organization", "")).containsExactly("organizations/globex");
    assertThat(query("user:nobody", "acme.dev/Project", "")).isEmpty();
  }

  @Test
  void aForbidOnTheOrganizationHidesItsProjects() throws Exception {
    create(ANNE, "", "acme.dev/Organization", "acme", Map.of());
    create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of());
    grant(ANNE, "organizations/acme", "member", BOB);
    grant(ANNE, "projects/ledger", "editor", BOB);
    assertThat(query(BOB, "acme.dev/Project", "")).containsExactly("projects/ledger");

    grant(ANNE, "organizations/acme", "blocked", BOB);

    assertThat(status(() -> get(BOB, "projects/ledger"))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    assertThat(query(BOB, "acme.dev/Project", "")).isEmpty();
    assertThat(check(ANNE, BOB, "projects/ledger", "can_edit")).isFalse();
  }

  @Test
  void aGroupGrantsWhatIsGrantedToItsMembers() throws Exception {
    create(ANNE, "", "acme.dev/Organization", "acme", Map.of());
    create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of());
    create(ANNE, "organizations/acme", "acme.dev/Group", "finance", Map.of());
    create(ANNE, "organizations/acme", "acme.dev/Group", "audit", Map.of());
    grant(ANNE, "groups/audit", "member", CAROL);
    grant(ANNE, "groups/finance", "member", "group:audit#member");
    grant(ANNE, "projects/ledger", "viewer", "group:finance#member");

    assertThat(get(CAROL, "projects/ledger").getName()).isEqualTo("projects/ledger");
    assertThat(query(CAROL, "acme.dev/Project", "")).containsExactly("projects/ledger");
    assertThat(status(() -> grant(ANNE, "groups/audit", "member", "group:finance#member")))
        .isEqualTo(GrpcStatus.FAILED_PRECONDITION);
    assertThat(access.listSubjects(ListSubjectsRequest.newBuilder().setSubject(ANNE).setObject("projects/ledger")
        .setPermission("can_view").setSubjectType("user").build()).await(10, TimeUnit.SECONDS).getSubjectsList())
        .containsExactly(ANNE, CAROL);
  }

  @Test
  void aConditionLimitsAGrantInTime() throws Exception {
    create(ANNE, "", "acme.dev/Organization", "acme", Map.of());
    create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of());
    access.createGrant(CreateGrantRequest.newBuilder().setSubject(ANNE).setGrant(Grant.newBuilder()
        .setObject("projects/ledger").setRelation("editor").setGrantee(BOB)
        .setCondition(Condition.newBuilder().setName("not_expired").setContext(Struct.newBuilder()
            .putFields("expires_at", com.google.protobuf.Value.newBuilder()
                .setStringValue("2026-06-01T00:00:00Z").build()))))
        .build()).await(10, TimeUnit.SECONDS);

    assertThat(check(ANNE, BOB, "projects/ledger", "can_edit", "2026-05-01T00:00:00Z")).isTrue();
    assertThat(check(ANNE, BOB, "projects/ledger", "can_edit", "2026-07-01T00:00:00Z")).isFalse();
  }

  @Test
  void anUpdateNamesTheRevisionThatItChanges() throws Exception {
    create(ANNE, "", "acme.dev/Organization", "acme", Map.of());
    Resource ledger = create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of());
    clock.move(Duration.ofMinutes(5));

    Resource renamed = data.updateResource(UpdateResourceRequest.newBuilder().setSubject(ANNE)
        .setResource(Resource.newBuilder().setName("projects/ledger").putFields("display_name", text("General")))
        .setUpdateMask(FieldMask.newBuilder().addPaths("display_name")).setRevision(1).build())
        .await(10, TimeUnit.SECONDS);

    assertThat(renamed.getFieldsMap().get("display_name").getStringValue()).isEqualTo("General");
    assertThat(renamed.getFieldsMap().get("revision").getIntegerValue()).isEqualTo(2);
    assertThat(renamed.getFieldsMap().get("update_time")).isNotEqualTo(ledger.getFieldsMap().get("update_time"));
    assertThat(status(() -> data.updateResource(UpdateResourceRequest.newBuilder().setSubject(ANNE)
        .setResource(Resource.newBuilder().setName("projects/ledger").putFields("display_name", text("Old")))
        .setUpdateMask(FieldMask.newBuilder().addPaths("display_name")).setRevision(1).build())
        .await(10, TimeUnit.SECONDS))).isEqualTo(GrpcStatus.ABORTED);
    assertThat(status(() -> data.updateResource(UpdateResourceRequest.newBuilder().setSubject(ANNE)
        .setResource(Resource.newBuilder().setName("projects/ledger").putFields("uid", text("x")))
        .setUpdateMask(FieldMask.newBuilder().addPaths("uid")).setRevision(2).build())
        .await(10, TimeUnit.SECONDS))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
  }

  @Test
  void aMoveNeedsTheCreatePermissionOnTheNewParent() throws Exception {
    create(ANNE, "", "acme.dev/Organization", "acme", Map.of());
    create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of());
    create(DAVE, "", "acme.dev/Organization", "globex", Map.of());
    grant(ANNE, "organizations/acme", "member", BOB);

    assertThat(status(() -> move(ANNE, "projects/ledger", "organizations/globex", 1)))
        .isEqualTo(GrpcStatus.PERMISSION_DENIED);
    grant(DAVE, "organizations/globex", "admin", ANNE);
    Resource moved = move(ANNE, "projects/ledger", "organizations/globex", 1);

    assertThat(moved.getFieldsMap().get("parent").getStringValue()).isEqualTo("organizations/globex");
    assertThat(status(() -> get(BOB, "projects/ledger"))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    assertThat(get(DAVE, "projects/ledger").getName()).isEqualTo("projects/ledger");
  }

  @Test
  void aDeletedResourceIsUndeletedUntilItIsPurged() throws Exception {
    create(ANNE, "", "acme.dev/Organization", "acme", Map.of());
    create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of());
    assertThat(status(() -> delete(ANNE, "organizations/acme", 1))).isEqualTo(GrpcStatus.FAILED_PRECONDITION);

    Resource deleted = delete(ANNE, "projects/ledger", 1);
    assertThat(deleted.getFieldsMap().get("purge_time").getTimestampValue().getSeconds())
        .isEqualTo(START.plus(Duration.ofDays(30)).getEpochSecond());
    assertThat(query(ANNE, "acme.dev/Project", "")).isEmpty();
    assertThat(data.queryResources(QueryResourcesRequest.newBuilder().setSubject(ANNE).setType("acme.dev/Project")
        .setShowDeleted(true).build()).await(10, TimeUnit.SECONDS).getResourcesCount()).isEqualTo(1);

    data.undeleteResource(UndeleteResourceRequest.newBuilder().setSubject(ANNE).setName("projects/ledger")
        .setRevision(2).build()).await(10, TimeUnit.SECONDS);
    assertThat(query(ANNE, "acme.dev/Project", "")).containsExactly("projects/ledger");

    delete(ANNE, "projects/ledger", 3);
    clock.move(Duration.ofDays(31));
    assertThat(data.purgeResources(PurgeResourcesRequest.getDefaultInstance()).await(10, TimeUnit.SECONDS)
        .getPurged()).isEqualTo(1);
    assertThat(status(() -> get(ANNE, "projects/ledger"))).isEqualTo(GrpcStatus.NOT_FOUND);
    create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of());
    assertThat(access.listGrants(ListGrantsRequest.newBuilder().setSubject(ANNE).setObject("projects/ledger")
        .build()).await(10, TimeUnit.SECONDS).getGrantsList()).extracting(Grant::getGrantee).containsExactly(ANNE);
  }

  @Test
  void theGrantsAndTheirChangesAreListed() throws Exception {
    create(ANNE, "", "acme.dev/Organization", "acme", Map.of());
    create(ANNE, "organizations/acme", "acme.dev/Project", "ledger", Map.of());
    grant(ANNE, "projects/ledger", "viewer", BOB);
    grant(ANNE, "projects/ledger", "viewer", BOB);
    access.deleteGrant(DeleteGrantRequest.newBuilder().setSubject(ANNE).setGrant(Grant.newBuilder()
        .setObject("projects/ledger").setRelation("viewer").setGrantee(BOB)).build()).await(10, TimeUnit.SECONDS);

    assertThat(access.listGrants(ListGrantsRequest.newBuilder().setSubject(ANNE).setObject("projects/ledger")
        .build()).await(10, TimeUnit.SECONDS).getGrantsList()).extracting(Grant::getRelation)
        .containsExactly("owner");
    List<Change> changes = access.listChanges(ListChangesRequest.newBuilder().setSubject(ANNE)
        .setType("acme.dev/Project").build()).await(10, TimeUnit.SECONDS).getChangesList();
    assertThat(changes).extracting(change -> change.getGrant().getRelation() + " " + change.getOperation())
        .containsExactly("owner GRANTED", "viewer GRANTED", "viewer REVOKED");
    assertThat(access.listChanges(ListChangesRequest.newBuilder().setSubject(BOB).setType("acme.dev/Project")
        .build()).await(10, TimeUnit.SECONDS).getChangesList()).isEmpty();
    assertThat(status(() -> grant(BOB, "projects/ledger", "viewer", CAROL))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    assertThat(status(() -> grant(ANNE, "projects/ledger", "can_view", CAROL)))
        .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
  }

  private Resource create(String subject, String parent, String type, String id, Map<String, Value> fields)
      throws Exception {
    return data.createResource(CreateResourceRequest.newBuilder().setSubject(subject).setParent(parent)
        .setResourceId(id).setResource(Resource.newBuilder().setType(type).putAllFields(fields)).build())
        .await(10, TimeUnit.SECONDS);
  }

  private Resource get(String subject, String name) throws Exception {
    return data.getResource(GetResourceRequest.newBuilder().setSubject(subject).setName(name).build())
        .await(10, TimeUnit.SECONDS);
  }

  private Resource move(String subject, String name, String parent, long revision) throws Exception {
    return data.moveResource(MoveResourceRequest.newBuilder().setSubject(subject).setName(name).setParent(parent)
        .setRevision(revision).build()).await(10, TimeUnit.SECONDS);
  }

  private Resource delete(String subject, String name, long revision) throws Exception {
    return data.deleteResource(DeleteResourceRequest.newBuilder().setSubject(subject).setName(name)
        .setRevision(revision).build()).await(10, TimeUnit.SECONDS);
  }

  private List<String> query(String subject, String type, String filter) throws Exception {
    return data.queryResources(QueryResourcesRequest.newBuilder().setSubject(subject).setType(type)
        .setFilter(filter).setOrderBy("name").build()).await(10, TimeUnit.SECONDS).getResourcesList().stream()
        .map(Resource::getName).toList();
  }

  private Grant grant(String subject, String object, String relation, String grantee) throws Exception {
    return access.createGrant(CreateGrantRequest.newBuilder().setSubject(subject).setGrant(Grant.newBuilder()
        .setObject(object).setRelation(relation).setGrantee(grantee)).build()).await(10, TimeUnit.SECONDS);
  }

  private boolean check(String subject, String principal, String object, String permission) throws Exception {
    return check(subject, principal, object, permission, START.toString());
  }

  private boolean check(String subject, String principal, String object, String permission, String now)
      throws Exception {
    return access.check(CheckRequest.newBuilder().setSubject(subject).setPrincipal(principal).setObject(object)
        .setPermission(permission).setContext(Struct.newBuilder().putFields("current_time",
            com.google.protobuf.Value.newBuilder().setStringValue(now).build()))
        .build()).await(10, TimeUnit.SECONDS).getAllowed();
  }

  private static Value text(String text) {
    return Value.newBuilder().setStringValue(text).build();
  }

  private static Value labels(String key, String value) {
    return Value.newBuilder().setMapValue(MapValue.newBuilder().putFields(key, text(value))).build();
  }

  private static GrpcStatus status(ThrowingCallable call) {
    GrpcStatus[] status = new GrpcStatus[1];
    assertThatThrownBy(call).isInstanceOfSatisfying(InvalidStatusException.class,
        failure -> status[0] = failure.actualStatus());
    return status[0];
  }
}
