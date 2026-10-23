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
import dev.horizon.spectrum.openfga.v1.TupleKeyWithoutCondition;
import dev.horizon.spectrum.openfga.v1.TypeDefinition;
import dev.horizon.spectrum.openfga.v1.User;
import dev.horizon.spectrum.openfga.v1.UserTypeFilter;
import dev.horizon.spectrum.openfga.v1.Userset;
import dev.horizon.spectrum.openfga.v1.WriteAuthorizationModelRequest;
import dev.horizon.spectrum.openfga.v1.WriteRequest;
import dev.horizon.spectrum.openfga.v1.WriteRequestDeletes;
import dev.horizon.spectrum.openfga.v1.WriteRequestWrites;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class Oracle {

  private final OpenFGAServiceGrpcClient openFga;
  private final String store;
  private final Map<String, String> singulars = new HashMap<>();

  Oracle(OpenFGAServiceGrpcClient openFga, Declaration declaration) throws Exception {
    this.openFga = openFga;
    this.store = openFga.createStore(CreateStoreRequest.newBuilder().setName("oracle").build()).await(TIMEOUT)
        .getId();
    Map<String, String> byName = new HashMap<>();
    for (Type type : declaration.getTypesList()) {
      byName.put(type.getName(), type.getDefinition().getType());
    }
    WriteAuthorizationModelRequest.Builder model = WriteAuthorizationModelRequest.newBuilder().setStoreId(store)
        .setSchemaVersion("1.1").putAllConditions(declaration.getConditionsMap());
    for (Type type : declaration.getTypesList()) {
      String plural = type.getPlural().isEmpty()
          ? type.getName().equals("pyrite.dev/Key") ? "keys" : "accounts"
          : type.getPlural();
      singulars.put(plural, type.getDefinition().getType());
      TypeDefinition.Builder definition = type.getDefinition().toBuilder();
      if (type.getParentsCount() > 0) {
        RelationMetadata.Builder parents = RelationMetadata.newBuilder();
        type.getParentsList().forEach(parent -> parents.addDirectlyRelatedUserTypes(RelationReference.newBuilder()
            .setType(byName.get(parent))));
        definition.putRelations("parent", Userset.newBuilder().setThis(DirectUserset.getDefaultInstance()).build())
            .setMetadata(Metadata.newBuilder(definition.getMetadata()).putRelations("parent", parents.build()));
      }
      model.addTypeDefinitions(definition);
    }
    openFga.writeAuthorizationModel(model.build()).await(TIMEOUT);
  }

  String object(String name) {
    int slash = name.indexOf('/');
    return singulars.get(name.substring(0, slash)) + ":" + name.substring(slash + 1);
  }

  void write(String name, String relation, String grantee, String condition, Struct context) throws Exception {
    TupleKey.Builder tuple = TupleKey.newBuilder().setObject(object(name)).setRelation(relation).setUser(grantee);
    if (!condition.isEmpty()) {
      tuple.setCondition(RelationshipCondition.newBuilder().setName(condition).setContext(context));
    }
    openFga.write(WriteRequest.newBuilder().setStoreId(store).setWrites(WriteRequestWrites.newBuilder()
        .addTupleKeys(tuple)).build()).await(TIMEOUT);
  }

  void parent(String name, String parent) throws Exception {
    openFga.write(WriteRequest.newBuilder().setStoreId(store).setWrites(WriteRequestWrites.newBuilder()
        .addTupleKeys(TupleKey.newBuilder().setObject(object(name)).setRelation("parent").setUser(object(parent))))
        .build()).await(TIMEOUT);
  }

  void move(String name, String from, String to) throws Exception {
    openFga.write(WriteRequest.newBuilder().setStoreId(store).setDeletes(WriteRequestDeletes.newBuilder()
        .addTupleKeys(TupleKeyWithoutCondition.newBuilder().setObject(object(name)).setRelation("parent")
            .setUser(object(from))))
        .build()).await(TIMEOUT);
    parent(name, to);
  }

  boolean check(String principal, String name, String relation, Struct context) throws Exception {
    return openFga.check(CheckRequest.newBuilder().setStoreId(store).setTupleKey(CheckRequestTupleKey.newBuilder()
        .setUser(principal).setRelation(relation).setObject(object(name))).setContext(context).build())
        .await(TIMEOUT).getAllowed();
  }

  Set<String> users(String name, String relation, Struct context) throws Exception {
    String object = object(name);
    int colon = object.indexOf(':');
    Set<String> users = new TreeSet<>();
    List<User> found = openFga.listUsers(ListUsersRequest.newBuilder().setStoreId(store)
        .setObject(dev.horizon.spectrum.openfga.v1.Object.newBuilder().setType(object.substring(0, colon))
            .setId(object.substring(colon + 1)))
        .setRelation(relation).addUserFilters(UserTypeFilter.newBuilder().setType("user")).setContext(context)
        .build()).await(TIMEOUT).getUsersList();
    for (User user : found) {
      users.add(user.hasWildcard() ? user.getWildcard().getType() + ":*"
          : user.getObject().getType() + ":" + user.getObject().getId());
    }
    return users;
  }
}
