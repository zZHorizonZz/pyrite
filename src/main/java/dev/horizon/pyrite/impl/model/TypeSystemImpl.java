package dev.horizon.pyrite.impl.model;

import dev.horizon.pyrite.schema.v1.Declaration;
import dev.horizon.pyrite.schema.v1.FlowDefinition;
import dev.horizon.pyrite.schema.v1.IdentifierKind;
import dev.horizon.pyrite.schema.v1.Type;
import dev.horizon.spectrum.openfga.v1.Condition;
import dev.horizon.spectrum.openfga.v1.DirectUserset;
import dev.horizon.spectrum.openfga.v1.Metadata;
import dev.horizon.spectrum.openfga.v1.RelationMetadata;
import dev.horizon.spectrum.openfga.v1.RelationReference;
import dev.horizon.spectrum.openfga.v1.TypeDefinition;
import dev.horizon.spectrum.openfga.v1.Userset;
import dev.horizon.spectrum.openfga.v1.WriteAuthorizationModelRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class TypeSystemImpl implements TypeSystem {

  private static final Pattern SINGULAR = Pattern.compile("[a-z][a-z0-9_]{0,49}");
  private static final Pattern PLURAL = Pattern.compile("[a-z][a-zA-Z0-9]{0,62}");

  private final Map<String, DeclaredType> byName = new LinkedHashMap<>();
  private final Map<String, DeclaredType> byPlural = new HashMap<>();
  private final Map<String, DeclaredType> bySingular = new HashMap<>();
  private final Map<String, Condition> conditions = new LinkedHashMap<>();
  private final Map<String, FlowDefinition> flows = new LinkedHashMap<>();
  private final Map<String, DeclaredKind> kinds = new LinkedHashMap<>();

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
    for (IdentifierKind kind : declaration.getIdentifierKindsList()) {
      if (kinds.put(kind.getName(), DeclaredKind.of(kind)) != null) {
        throw new IllegalArgumentException("The identifier kind " + kind.getName() + " is declared twice.");
      }
    }
    DeclarationValidator validator = new DeclarationValidator(this);
    validator.references();
    validator.permissions();
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
  public List<DeclaredKind> identifierKinds() {
    return List.copyOf(kinds.values());
  }

  @Override
  public Optional<DeclaredKind> identifierKind(String name) {
    return Optional.ofNullable(kinds.get(name));
  }

  @Override
  public Optional<DeclaredKind> identifierKindOf(String value) {
    return kinds.values().stream().filter(kind -> kind.matches(value)).findFirst();
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
        definition.putRelations(name, relation.rewrite());
        metadata.putRelations(name, RelationMetadata.newBuilder()
            .addAllDirectlyRelatedUserTypes(relation.direct()).build());
      });
      if (!type.parents().isEmpty()) {
        definition.putRelations(PARENT, Userset.newBuilder().setThis(DirectUserset.getDefaultInstance()).build());
        metadata.putRelations(PARENT, RelationMetadata.newBuilder().addAllDirectlyRelatedUserTypes(parents(type)
            .stream().map(parent -> RelationReference.newBuilder().setType(parent.singular()).build()).toList())
            .build());
      }
      model.addTypeDefinitions(definition.setMetadata(metadata));
    }
    return model.build();
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
      if (!type.getPlural().isEmpty()) {
        throw new IllegalArgumentException("The type " + type.getName() + " is Pyrite's own, which names its"
            + " resources itself; its declaration leaves plural empty.");
      }
      types.add(type.toBuilder().setPlural(key ? "keys" : "accounts").build());
    }
    return types;
  }

  private static DeclaredType compile(Type type) {
    if (type.getName().isBlank()) {
      throw new IllegalArgumentException("A type has no name.");
    }
    String where = "The type " + type.getName();
    matches(SINGULAR, type.getDefinition().getType(), where + " has the singular name ");
    matches(PLURAL, type.getPlural(), where + " has the plural name ");
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
        throw new IllegalArgumentException(where + " declares the relation parent, which is Pyrite's: it holds the"
            + " resource that a resource is under.");
      }
      List<RelationReference> direct = metadata.getOrDefault(name, RelationMetadata.getDefaultInstance())
          .getDirectlyRelatedUserTypesList();
      if (direct.isEmpty() == takesDirectly(rewrite)) {
        throw new IllegalArgumentException("The relation " + name + " of the type " + type.getName() + " has"
            + " directly related types exactly when its rewrite has this.");
      }
      relations.put(name, new Definition(rewrite, direct));
    });
    return new DeclaredType(type, relations);
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
