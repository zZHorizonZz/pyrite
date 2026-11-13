package dev.horizon.pyrite.access;

import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.access.v1.AccessGrpcService;
import dev.horizon.pyrite.access.v1.Change;
import dev.horizon.pyrite.access.v1.CheckRequest;
import dev.horizon.pyrite.access.v1.CheckResponse;
import dev.horizon.pyrite.access.v1.CreateGrantRequest;
import dev.horizon.pyrite.access.v1.CreateObjectRequest;
import dev.horizon.pyrite.access.v1.DeleteGrantRequest;
import dev.horizon.pyrite.access.v1.DeleteObjectRequest;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.access.v1.ListChangesRequest;
import dev.horizon.pyrite.access.v1.ListChangesResponse;
import dev.horizon.pyrite.access.v1.ListGrantsRequest;
import dev.horizon.pyrite.access.v1.ListGrantsResponse;
import dev.horizon.pyrite.access.v1.ListObjectsRequest;
import dev.horizon.pyrite.access.v1.ListObjectsResponse;
import dev.horizon.pyrite.access.v1.ListSubjectsRequest;
import dev.horizon.pyrite.access.v1.ListSubjectsResponse;
import dev.horizon.pyrite.declaration.ResourceType;
import dev.horizon.pyrite.declaration.TypeSystem;
import dev.horizon.pyrite.deployment.Deployment;
import dev.horizon.pyrite.deployment.Schema;
import dev.horizon.pyrite.resource.ObjectRef;
import dev.horizon.pyrite.resource.Resource;
import dev.horizon.pyrite.resource.Subject;
import dev.horizon.spectrum.openfga.v1.TupleChange;
import dev.horizon.spectrum.openfga.v1.TupleOperation;
import dev.horizon.spectrum.openfga.v1.UserTypeFilter;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;
import java.util.Optional;

/**
 * The service {@code pyrite.access.v1.Access}: objects, the grants on them, and the checks and lists of who can do
 * what.
 *
 * <p>
 * Every call runs as the subject of its request, which fails with {@code INVALID_ARGUMENT} when it is not a principal
 * of the form {@code type:id}. A call on a resource fails with {@code NOT_FOUND} when Pyrite does not know it, and
 * with {@code PERMISSION_DENIED} when the subject lacks the relation that the call checks: the type's create
 * permission on the parent, or its grant permission on the resource. A refusal of OpenFGA ends the call with the
 * status of OpenFGA.
 */
public final class AccessEndpoint extends AccessGrpcService {

  private final Deployment deployment;

  public AccessEndpoint(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  public Future<Empty> createObject(CreateObjectRequest request) {
    return deployment.schema().compose(schema -> {
      for (String own : List.of(TypeSystem.KEY, TypeSystem.ACCOUNT)) {
        Optional<ResourceType> type = schema.types().type(own);
        if (type.isPresent() && request.getName().startsWith(type.get().plural() + "/")) {
          return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The resource "
              + request.getName() + " is of the type " + own + ", which its own service creates."));
        }
      }
      return schema.resources().create(Subject.parse(request.getSubject()), request.getName(), request.getParent(),
          request.getContext());
    }).map(Empty.getDefaultInstance()).recover(PyriteException::fail);
  }

  @Override
  public Future<Empty> deleteObject(DeleteObjectRequest request) {
    return deployment.schema().compose(schema -> schema.resources().require(request.getName())
        .compose(found -> found.require(Subject.parse(request.getSubject()), found.type().permissions().getGrant(),
            request.getContext()))
        .compose(found -> schema.forget(request.getName())))
        .map(Empty.getDefaultInstance()).recover(PyriteException::fail);
  }

  @Override
  public Future<ListObjectsResponse> listObjects(ListObjectsRequest request) {
    return deployment.schema().compose(schema -> schema.type(request.getType()).compose(type -> {
      if (!type.relations().containsKey(request.getPermission())) {
        return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The type " + type.name()
            + " has no relation " + request.getPermission() + "."));
      }
      return schema.openFga().objects(type.singular(), request.getPermission(),
          Subject.parse(request.getSubject()).toString(), request.getContext())
          .map(objects -> ListObjectsResponse.newBuilder().addAllNames(objects.stream().flatMap(object -> ObjectRef
              .parse(object).stream()).map(object -> type.resource(object.id())).sorted().toList()).build());
    })).recover(PyriteException::fail);
  }

  @Override
  public Future<Grant> createGrant(CreateGrantRequest request) {
    return deployment.schema().compose(schema -> schema.resources().require(request.getGrant().getObject()))
        .compose(target -> target.require(Subject.parse(request.getSubject()), target.type().permissions()
            .getGrant(), request.getContext()))
        .compose(target -> target.grant(request.getGrant(), request.getContext()))
        .map(request.getGrant()).recover(PyriteException::fail);
  }

  @Override
  public Future<Empty> deleteGrant(DeleteGrantRequest request) {
    return deployment.schema().compose(schema -> schema.resources().require(request.getGrant().getObject()))
        .compose(target -> target.require(Subject.parse(request.getSubject()), target.type().permissions()
            .getGrant(), request.getContext()))
        .compose(target -> target.revoke(request.getGrant()))
        .map(Empty.getDefaultInstance()).recover(PyriteException::fail);
  }

  @Override
  public Future<CheckResponse> check(CheckRequest request) {
    return deployment.schema().compose(schema -> {
      Subject subject = Subject.parse(request.getSubject());
      Subject principal = request.getPrincipal().isEmpty() ? subject : Subject.parse(request.getPrincipal());
      return schema.resources().require(request.getObject()).compose(found -> found.requireRelation(request
          .getPermission())).compose(found -> {
            Future<Resource> asking = principal.equals(subject) ? Future.succeededFuture(found)
                : found.require(subject, found.type().permissions().getGrant(), request.getContext());
            return asking.compose(ignored -> found.check(principal.toString(), request.getPermission(),
                request.getContext()));
          });
    }).map(allowed -> CheckResponse.newBuilder().setAllowed(allowed).build()).recover(PyriteException::fail);
  }

  @Override
  public Future<ListGrantsResponse> listGrants(ListGrantsRequest request) {
    return deployment.schema().compose(schema -> schema.resources().require(request.getObject())
        .compose(found -> found.require(Subject.parse(request.getSubject()), found.type().permissions().getGrant(),
            request.getContext()))
        .compose(found -> found.grants(request.getRelation(), request.getGrantee(),
            schema.options().pageSize(request.getPageSize()), request.getPageToken())))
        .map(page -> ListGrantsResponse.newBuilder().addAllGrants(page.items())
            .setNextPageToken(page.nextPageToken()).build())
        .recover(PyriteException::fail);
  }

  @Override
  public Future<ListSubjectsResponse> listSubjects(ListSubjectsRequest request) {
    return deployment.schema().compose(schema -> {
      if (request.getSubjectType().isEmpty()) {
        return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The request names no subject"
            + " type."));
      }
      String[] parts = request.getSubjectType().split("#", 2);
      UserTypeFilter filter = parts.length == 2
          ? UserTypeFilter.newBuilder().setType(parts[0]).setRelation(parts[1]).build()
          : UserTypeFilter.newBuilder().setType(parts[0]).build();
      return schema.resources().require(request.getObject())
          .compose(found -> found.requireRelation(request.getPermission()))
          .compose(found -> found.require(Subject.parse(request.getSubject()), found.type().permissions().getGrant(),
              request.getContext()))
          .compose(found -> found.subjects(request.getPermission(), filter, request.getContext()));
    }).map(subjects -> ListSubjectsResponse.newBuilder().addAllSubjects(subjects).build())
        .recover(PyriteException::fail);
  }

  @Override
  public Future<ListChangesResponse> listChanges(ListChangesRequest request) {
    return deployment.schema().compose(schema -> {
      Subject subject = Subject.parse(request.getSubject());
      return schema.type(request.getType())
          .compose(type -> schema.openFga().changes(type.singular(), schema.options().pageSize(request.getPageSize()),
              request.getPageToken(), request.hasStartTime() ? Optional.of(request.getStartTime()) : Optional.empty())
              .compose(changes -> {
                List<Future<Optional<Change>>> visible = changes.getChangesList().stream()
                    .map(change -> visible(schema, subject, change, request.getContext())).toList();
                return Future.all(visible).map(all -> {
                  ListChangesResponse.Builder response = ListChangesResponse.newBuilder()
                      .setNextPageToken(changes.getContinuationToken());
                  visible.forEach(change -> change.result().ifPresent(response::addChanges));
                  return response.build();
                });
              }));
    }).recover(PyriteException::fail);
  }

  private static Future<Optional<Change>> visible(Schema schema, Subject subject, TupleChange change,
      Struct context) {
    Optional<Grant> grant = schema.resources().grant(change.getTupleKey());
    if (grant.isEmpty() || change.getTupleKey().getRelation().equals(TypeSystem.PARENT)) {
      return Future.succeededFuture(Optional.empty());
    }
    return schema.resources().get(grant.get().getObject())
        .compose(found -> found.isEmpty() ? Future.succeededFuture(false)
            : found.get().check(subject.toString(), found.get().type().permissions().getGrant(), context))
        .map(allowed -> allowed ? Optional.of(Change.newBuilder().setGrant(grant.get())
            .setOperation(change.getOperation() == TupleOperation.TUPLE_OPERATION_DELETE
                ? Change.Operation.REVOKED
                : Change.Operation.GRANTED)
            .setTime(change.getTimestamp()).build()) : Optional.empty());
  }
}
