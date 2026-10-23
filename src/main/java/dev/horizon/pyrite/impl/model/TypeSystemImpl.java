package dev.horizon.pyrite.impl.model;

import dev.horizon.pyrite.authentication.impl.AccountType;
import dev.horizon.pyrite.authentication.impl.FactorType;
import dev.horizon.pyrite.authentication.impl.IdentifierType;
import dev.horizon.pyrite.authentication.impl.SessionType;
import dev.horizon.pyrite.crypto.impl.KeyType;
import dev.horizon.pyrite.crypto.impl.KeyVersionType;
import dev.horizon.pyrite.impl.resource.Resource;
import dev.horizon.pyrite.schema.v1.Declaration;
import dev.horizon.pyrite.schema.v1.Field;
import dev.horizon.pyrite.schema.v1.FlowDefinition;
import dev.horizon.pyrite.schema.v1.Permissions;
import dev.horizon.pyrite.schema.v1.Type;
import dev.horizon.spectrum.openfga.v1.Condition;
import dev.horizon.spectrum.openfga.v1.Difference;
import dev.horizon.spectrum.openfga.v1.DirectUserset;
import dev.horizon.spectrum.openfga.v1.Metadata;
import dev.horizon.spectrum.openfga.v1.ObjectRelation;
import dev.horizon.spectrum.openfga.v1.RelationMetadata;
import dev.horizon.spectrum.openfga.v1.RelationReference;
import dev.horizon.spectrum.openfga.v1.TupleToUserset;
import dev.horizon.spectrum.openfga.v1.TypeDefinition;
import dev.horizon.spectrum.openfga.v1.Userset;
import dev.horizon.spectrum.openfga.v1.Usersets;
import dev.horizon.spectrum.openfga.v1.WriteAuthorizationModelRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class TypeSystemImpl implements TypeSystem {

  private static final Pattern SINGULAR = Pattern.compile("[a-z][a-z0-9_]{0,49}");
  private static final Pattern PLURAL = Pattern.compile("[a-z][a-zA-Z0-9]{0,62}");
  private static final Pattern IDENTIFIER_NAME = Pattern.compile("[a-z][a-z0-9_]{0,62}");

  private final Map<String, DeclaredType> byName = new LinkedHashMap<>();
  private final Map<String, DeclaredType> byPlural = new HashMap<>();
  private final Map<String, DeclaredType> bySingular = new HashMap<>();
  private final Map<String, Condition> conditions = new LinkedHashMap<>();
  private final Set<String> fromParent = new HashSet<>();
  private final Map<String, Userset> walked = new HashMap<>();
  private final Map<String, Map<String, Userset>> hidden = new HashMap<>();
  private final Map<String, FlowDefinition> flows = new LinkedHashMap<>();

  public TypeSystemImpl(Declaration declaration) {
    declaration.getConditionsMap().forEach((name, condition) -> {
      if (!name.equals(condition.getName())) {
        throw new IllegalArgumentException("The condition " + name + " is named " + condition.getName() + ".");
      }
      conditions.put(name, condition);
    });
    for (Type type : expand(declaration)) {
      DeclaredType compiled = compile(type);
      if (byName.put(compiled.name(), compiled) != null) {
        throw new IllegalArgumentException("The type " + compiled.name() + " is declared twice.");
      }
      boolean reserved = compiled.singular().equals(USER) && !compiled.name().equals(ACCOUNT);
      if (reserved || bySingular.put(compiled.singular(), compiled) != null) {
        throw new IllegalArgumentException("The type " + compiled.name() + " has the singular name "
            + compiled.singular() + ", which " + (reserved ? "is Pyrite's accounts'" : "another type has") + ".");
      }
      if (byPlural.put(compiled.plural(), compiled) != null) {
        throw new IllegalArgumentException("The type " + compiled.name() + " has the plural name "
            + compiled.plural() + ", which another type has.");
      }
    }
    DeclarationValidator validator = new DeclarationValidator(this);
    validator.references();
    boolean changed = true;
    while (changed) {
      changed = false;
      for (DeclaredType type : byName.values()) {
        for (Map.Entry<String, Definition> relation : type.relations().entrySet()) {
          if (!readsFromParent(type, relation.getKey()) && readsFromParent(type, relation.getValue().rewrite())) {
            fromParent.add(type.singular() + "#" + relation.getKey());
            changed = true;
          }
        }
      }
    }
    validator.inheritance();
    validator.permissions();
    for (DeclaredType type : byName.values()) {
      for (Map.Entry<String, Definition> relation : type.relations().entrySet()) {
        if (readsFromParent(type, relation.getKey())) {
          walked.put(type.singular() + "#" + relation.getKey(), split(type, relation.getKey(),
              relation.getValue().rewrite(), new int[1]));
        }
      }
    }
    for (FlowDefinition flow : declaration.getFlowsList()) {
      if (flows.put(flow.getName(), flow) != null) {
        throw new IllegalArgumentException("The flow " + flow.getName() + " is declared twice.");
      }
    }
    validator.flows();
  }

  @Override
  public Optional<FlowDefinition> flow(String name) {
    return Optional.ofNullable(flows.get(name));
  }

  @Override
  public List<FlowDefinition> flows() {
    return List.copyOf(flows.values());
  }

  @Override
  public Optional<DeclaredType> type(String name) {
    return Optional.ofNullable(byName.get(name));
  }

  @Override
  public Optional<DeclaredType> singular(String singular) {
    return Optional.ofNullable(bySingular.get(singular));
  }

  @Override
  public Optional<DeclaredType> typeOf(String resource) {
    int slash = resource.indexOf('/');
    return slash < 0 ? Optional.empty() : Optional.ofNullable(byPlural.get(resource.substring(0, slash)));
  }

  @Override
  public List<DeclaredType> types() {
    return List.copyOf(byName.values());
  }

  @Override
  public Map<String, Condition> conditions() {
    return Map.copyOf(conditions);
  }

  @Override
  public List<DeclaredType> parents(DeclaredType type) {
    return type.parents().stream().map(byName::get).toList();
  }

  @Override
  public List<DeclaredType> children(DeclaredType type) {
    return byName.values().stream().filter(child -> child.parents().contains(type.name())).toList();
  }

  @Override
  public Set<DeclaredType> ancestors(DeclaredType type) {
    Set<DeclaredType> ancestors = new LinkedHashSet<>();
    List<DeclaredType> frontier = new ArrayList<>(parents(type));
    while (!frontier.isEmpty()) {
      DeclaredType next = frontier.removeFirst();
      if (ancestors.add(next)) {
        frontier.addAll(parents(next));
      }
    }
    return ancestors;
  }

  @Override
  public boolean readsFromParent(DeclaredType type, String relation) {
    return fromParent.contains(type.singular() + "#" + relation);
  }

  @Override
  public Userset rewrite(DeclaredType type, String relation) {
    return walked.getOrDefault(type.singular() + "#" + relation, type.relations().get(relation).rewrite());
  }

  @Override
  public Set<Leaf> leaves(DeclaredType type, String relation) {
    Set<Leaf> leaves = new LinkedHashSet<>();
    leaves(type, relation, leaves, new HashSet<>());
    return leaves;
  }

  @Override
  public WriteAuthorizationModelRequest model(String store) {
    WriteAuthorizationModelRequest.Builder model = WriteAuthorizationModelRequest.newBuilder().setStoreId(store)
        .setSchemaVersion("1.1").putAllConditions(conditions);
    if (!byName.containsKey(ACCOUNT)) {
      model.addTypeDefinitions(TypeDefinition.newBuilder().setType(USER));
    }
    for (DeclaredType type : byName.values()) {
      TypeDefinition.Builder definition = TypeDefinition.newBuilder().setType(type.singular());
      Metadata.Builder metadata = Metadata.newBuilder();
      type.relations().forEach((name, relation) -> {
        boolean local = !readsFromParent(type, name);
        if (local || !relation.direct().isEmpty()) {
          definition.putRelations(name, local ? relation.rewrite()
              : Userset.newBuilder().setThis(DirectUserset.getDefaultInstance()).build());
          metadata.putRelations(name, RelationMetadata.newBuilder()
              .addAllDirectlyRelatedUserTypes(relation.direct()).build());
        }
      });
      definition.putAllRelations(hidden.getOrDefault(type.singular(), Map.of()));
      model.addTypeDefinitions(definition.setMetadata(metadata));
    }
    return model.build();
  }

  private void leaves(DeclaredType type, String relation, Set<Leaf> leaves, Set<Leaf> visited) {
    if (!visited.add(new Leaf(type, relation))) {
      return;
    }
    if (readsFromParent(type, relation)) {
      leaves(type, relation, rewrite(type, relation), leaves, visited);
    } else {
      leaves.add(new Leaf(type, relation));
    }
  }

  private void leaves(DeclaredType type, String relation, Userset userset, Set<Leaf> leaves, Set<Leaf> visited) {
    switch (userset.getUsersetCase()) {
      case THIS -> leaves.add(new Leaf(type, relation));
      case COMPUTED_USERSET -> leaves(type, userset.getComputedUserset().getRelation(), leaves, visited);
      case TUPLE_TO_USERSET -> parents(type).forEach(parent -> leaves(parent,
          userset.getTupleToUserset().getComputedUserset().getRelation(), leaves, visited));
      case UNION -> userset.getUnion().getChildList()
          .forEach(child -> leaves(type, relation, child, leaves, visited));
      case INTERSECTION -> userset.getIntersection().getChildList()
          .forEach(child -> leaves(type, relation, child, leaves, visited));
      case DIFFERENCE -> leaves(type, relation, userset.getDifference().getBase(), leaves, visited);
      case USERSET_NOT_SET -> {
      }
    }
  }

  private Userset split(DeclaredType type, String relation, Userset userset, int[] next) {
    if (!readsFromParent(type, userset)) {
      if (userset.hasThis() || userset.hasComputedUserset()) {
        return userset;
      }
      String name = relation + "." + ++next[0];
      hidden.computeIfAbsent(type.singular(), ignored -> new LinkedHashMap<>()).put(name, direct(relation, userset));
      return Userset.newBuilder().setComputedUserset(ObjectRelation.newBuilder().setRelation(name)).build();
    }
    return switch (userset.getUsersetCase()) {
      case UNION -> Userset.newBuilder().setUnion(Usersets.newBuilder().addAllChild(userset.getUnion()
          .getChildList().stream().map(child -> split(type, relation, child, next)).toList())).build();
      case INTERSECTION -> Userset.newBuilder().setIntersection(Usersets.newBuilder().addAllChild(userset
          .getIntersection().getChildList().stream().map(child -> split(type, relation, child, next)).toList()))
          .build();
      case DIFFERENCE -> Userset.newBuilder().setDifference(Difference.newBuilder()
          .setBase(split(type, relation, userset.getDifference().getBase(), next))
          .setSubtract(split(type, relation, userset.getDifference().getSubtract(), next))).build();
      default -> userset;
    };
  }

  private static Userset direct(String relation, Userset userset) {
    return switch (userset.getUsersetCase()) {
      case THIS -> Userset.newBuilder().setComputedUserset(ObjectRelation.newBuilder().setRelation(relation)).build();
      case UNION -> Userset.newBuilder().setUnion(Usersets.newBuilder().addAllChild(userset.getUnion()
          .getChildList().stream().map(child -> direct(relation, child)).toList())).build();
      case INTERSECTION -> Userset.newBuilder().setIntersection(Usersets.newBuilder().addAllChild(userset
          .getIntersection().getChildList().stream().map(child -> direct(relation, child)).toList())).build();
      case DIFFERENCE -> Userset.newBuilder().setDifference(Difference.newBuilder()
          .setBase(direct(relation, userset.getDifference().getBase()))
          .setSubtract(direct(relation, userset.getDifference().getSubtract()))).build();
      default -> userset;
    };
  }

  private boolean readsFromParent(DeclaredType type, Userset userset) {
    return switch (userset.getUsersetCase()) {
      case COMPUTED_USERSET -> readsFromParent(type, userset.getComputedUserset().getRelation());
      case TUPLE_TO_USERSET -> userset.getTupleToUserset().getTupleset().getRelation().equals(PARENT);
      case UNION -> userset.getUnion().getChildList().stream().anyMatch(child -> readsFromParent(type, child));
      case INTERSECTION -> userset.getIntersection().getChildList().stream()
          .anyMatch(child -> readsFromParent(type, child));
      case DIFFERENCE -> readsFromParent(type, userset.getDifference().getBase())
          || readsFromParent(type, userset.getDifference().getSubtract());
      default -> false;
    };
  }

  private static List<Type> expand(Declaration declaration) {
    List<Type> types = new ArrayList<>();
    for (Type type : declaration.getTypesList()) {
      boolean key = type.getName().equals(KEY);
      boolean account = type.getName().equals(ACCOUNT);
      if (type.getName().startsWith("pyrite.dev/") && !key && !account) {
        throw new IllegalArgumentException("The type " + type.getName() + " is Pyrite's; a declaration does not"
            + " declare it.");
      }
      if (!key && !account) {
        types.add(type);
        continue;
      }
      if (!type.getPlural().isEmpty() || !type.getTable().isEmpty() || type.getFieldsCount() > 0) {
        throw new IllegalArgumentException("The type " + type.getName() + " is Pyrite's own, whose table Pyrite"
            + " has; its declaration leaves plural, table and fields empty.");
      }
      Type own = type.toBuilder().setPlural(key ? "keys" : "accounts").setTable(key ? "pyrite_key" : "pyrite_account")
          .addAllFields(key ? KeyType.FIELDS : AccountType.FIELDS).build();
      types.add(own);
      if (key) {
        types.add(beneath(KEY_VERSION, "key_version", "keyVersions", "pyrite_key_version", KeyVersionType.FIELDS, own));
      } else {
        types.add(beneath(IDENTIFIER, "identifier", "identifiers", "pyrite_identifier", IdentifierType.FIELDS, own));
        types.add(beneath(FACTOR, "factor", "factors", "pyrite_factor", FactorType.FIELDS, own));
        types.add(beneath(SESSION, "session", "sessions", "pyrite_session", SessionType.FIELDS, own));
      }
    }
    return types;
  }

  private static Type beneath(String name, String singular, String plural, String table, List<Field> fields,
      Type parent) {
    TypeDefinition.Builder definition = TypeDefinition.newBuilder().setType(singular);
    for (String relation : List.of("viewer", "admin")) {
      definition.putRelations(relation, Userset.newBuilder().setTupleToUserset(TupleToUserset.newBuilder()
          .setTupleset(ObjectRelation.newBuilder().setRelation(PARENT))
          .setComputedUserset(ObjectRelation.newBuilder().setRelation(relation))).build());
    }
    return Type.newBuilder().setName(name).setPlural(plural).addParents(parent.getName()).setTable(table)
        .addAllFields(fields).setDefinition(definition)
        .setPermissions(Permissions.newBuilder().setCreate("admin").setRead("viewer").setUpdate("admin")
            .setDelete("admin").setGrant("admin"))
        .setRetention(parent.getRetention()).build();
  }

  private static DeclaredType compile(Type type) {
    if (type.getName().isBlank()) {
      throw new IllegalArgumentException("A type has no name.");
    }
    String where = "The type " + type.getName();
    matches(SINGULAR, type.getDefinition().getType(), where + " has the singular name ");
    matches(PLURAL, type.getPlural(), where + " has the plural name ");
    if (type.getTable().isBlank()) {
      throw new IllegalArgumentException(where + " has no table.");
    }
    if (type.getRetention().getSeconds() < 0 || type.getRetention().getNanos() < 0) {
      throw new IllegalArgumentException(where + " has a negative retention.");
    }
    Map<String, Field.Kind> fields = new LinkedHashMap<>();
    for (Field field : type.getFieldsList()) {
      matches(IDENTIFIER_NAME, field.getName(), where + " has the field ");
      if (field.getKind() == Field.Kind.KIND_UNSPECIFIED || field.getKind() == Field.Kind.UNRECOGNIZED) {
        throw new IllegalArgumentException(where + " has the field " + field.getName() + " with no kind.");
      }
      if (fields.put(field.getName(), field.getKind()) != null) {
        throw new IllegalArgumentException(where + " has the field " + field.getName() + " twice.");
      }
    }
    for (Field standard : Resource.STANDARD) {
      if (fields.get(standard.getName()) != standard.getKind()) {
        throw new IllegalArgumentException(where + " lacks the standard field " + standard.getName() + " of the"
            + " kind " + standard.getKind() + ".");
      }
    }
    Map<String, RelationMetadata> metadata = type.getDefinition().getMetadata().getRelationsMap();
    for (String described : metadata.keySet()) {
      if (!type.getDefinition().getRelationsMap().containsKey(described)) {
        throw new IllegalArgumentException(where + " has metadata for " + described + ", which is not a relation.");
      }
    }
    Map<String, Definition> relations = new LinkedHashMap<>();
    type.getDefinition().getRelationsMap().forEach((name, rewrite) -> {
      matches(SINGULAR, name, where + " has the relation ");
      if (name.equals(PARENT)) {
        throw new IllegalArgumentException(where + " declares the relation parent, which is Pyrite's: its parents"
            + " come from the parent field.");
      }
      List<RelationReference> direct = metadata.getOrDefault(name, RelationMetadata.getDefaultInstance())
          .getDirectlyRelatedUserTypesList();
      if (direct.isEmpty() == takesDirectly(rewrite)) {
        throw new IllegalArgumentException("The relation " + name + " of the type " + type.getName() + " has"
            + " directly related types exactly when its rewrite has this.");
      }
      relations.put(name, new Definition(rewrite, direct));
    });
    return new DeclaredType(type, relations, fields);
  }

  private static boolean takesDirectly(Userset userset) {
    return switch (userset.getUsersetCase()) {
      case THIS -> true;
      case UNION -> userset.getUnion().getChildList().stream().anyMatch(TypeSystemImpl::takesDirectly);
      case INTERSECTION -> userset.getIntersection().getChildList().stream().anyMatch(TypeSystemImpl::takesDirectly);
      case DIFFERENCE -> takesDirectly(userset.getDifference().getBase())
          || takesDirectly(userset.getDifference().getSubtract());
      default -> false;
    };
  }

  private static void matches(Pattern pattern, String value, String where) {
    if (!pattern.matcher(value).matches()) {
      throw new IllegalArgumentException(where + value + ", which does not match " + pattern.pattern() + ".");
    }
  }
}
