package dev.horizon.pyrite;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import com.google.protobuf.Struct;
import dev.horizon.pyrite.schema.v1.Declaration;
import dev.horizon.pyrite.schema.v1.Type;
import dev.horizon.spectrum.openfga.v1.CheckRequest;
import dev.horizon.spectrum.openfga.v1.CheckRequestTupleKey;
import dev.horizon.spectrum.openfga.v1.CreateStoreRequest;
import dev.horizon.spectrum.openfga.v1.DirectUserset;
import dev.horizon.spectrum.openfga.v1.ListUsersRequest;
import dev.horizon.spectrum.openfga.v1.Metadata;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import dev.horizon.spectrum.openfga.v1.RelationMetadata;
import dev.horizon.spectrum.openfga.v1.RelationReference;
import dev.horizon.spectrum.openfga.v1.RelationshipCondition;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.openfga.v1.TypeDefinition;
import dev.horizon.spectrum.openfga.v1.User;
import dev.horizon.spectrum.openfga.v1.UserTypeFilter;
import dev.horizon.spectrum.openfga.v1.Userset;
import dev.horizon.spectrum.openfga.v1.WriteAuthorizationModelRequest;
import dev.horizon.spectrum.openfga.v1.WriteRequest;
import dev.horizon.spectrum.openfga.v1.WriteRequestWrites;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class Oracle {

  private final OpenFGAServiceGrpcClient openFga;
  private final String store;

  public Oracle(OpenFGAServiceGrpcClient openFga, Declaration declaration) throws Exception {
    this.openFga = openFga;
    this.store = openFga.createStore(CreateStoreRequest.newBuilder().setName("oracle").build()).await(TIMEOUT)
        .getId();
    WriteAuthorizationModelRequest.Builder model = WriteAuthorizationModelRequest.newBuilder().setStoreId(store)
        .setSchemaVersion("1.1").putAllConditions(declaration.getConditionsMap());
    for (Type type : declaration.getTypesList()) {
      TypeDefinition.Builder definition = type.getDefinition().toBuilder().setType(type.getName());
      if (type.getParentsCount() > 0) {
        RelationMetadata.Builder parents = RelationMetadata.newBuilder();
        type.getParentsList().forEach(parent -> parents.addDirectlyRelatedUserTypes(RelationReference.newBuilder()
            .setType(parent)));
        definition.putRelations("parent", Userset.newBuilder().setThis(DirectUserset.getDefaultInstance()).build())
            .setMetadata(Metadata.newBuilder(definition.getMetadata()).putRelations("parent", parents.build()));
      }
      model.addTypeDefinitions(definition);
    }
    openFga.writeAuthorizationModel(model.build()).await(TIMEOUT);
  }

  public void write(String name, String relation, String grantee, String condition, Struct context) throws Exception {
    TupleKey.Builder tuple = TupleKey.newBuilder().setObject(name).setRelation(relation).setUser(grantee);
    if (!condition.isEmpty()) {
      tuple.setCondition(RelationshipCondition.newBuilder().setName(condition).setContext(context));
    }
    openFga.write(WriteRequest.newBuilder().setStoreId(store).setWrites(WriteRequestWrites.newBuilder()
        .addTupleKeys(tuple)).build()).await(TIMEOUT);
  }

  public void parent(String name, String parent) throws Exception {
    openFga.write(WriteRequest.newBuilder().setStoreId(store).setWrites(WriteRequestWrites.newBuilder()
        .addTupleKeys(TupleKey.newBuilder().setObject(name).setRelation("parent").setUser(parent)))
        .build()).await(TIMEOUT);
  }

  public boolean check(String principal, String name, String relation, Struct context) throws Exception {
    return openFga.check(CheckRequest.newBuilder().setStoreId(store).setTupleKey(CheckRequestTupleKey.newBuilder()
        .setUser(principal).setRelation(relation).setObject(name)).setContext(context).build())
        .await(TIMEOUT).getAllowed();
  }

  public Set<String> users(String name, String relation, Struct context) throws Exception {
    int colon = name.indexOf(':');
    Set<String> users = new TreeSet<>();
    List<User> found = openFga.listUsers(ListUsersRequest.newBuilder().setStoreId(store)
        .setObject(dev.horizon.spectrum.openfga.v1.Object.newBuilder().setType(name.substring(0, colon))
            .setId(name.substring(colon + 1)))
        .setRelation(relation).addUserFilters(UserTypeFilter.newBuilder().setType("pyrite.dev/Account")).setContext(context)
        .build()).await(TIMEOUT).getUsersList();
    for (User user : found) {
      users.add(user.hasWildcard() ? user.getWildcard().getType() + ":*"
          : user.getObject().getType() + ":" + user.getObject().getId());
    }
    return users;
  }
}
