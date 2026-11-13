package dev.horizon.pyrite.declaration;

import dev.horizon.pyrite.schema.v1.Permissions;
import dev.horizon.pyrite.schema.v1.Type;
import dev.horizon.spectrum.openfga.v1.RelationMetadata;
import dev.horizon.spectrum.openfga.v1.RelationReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A type of resources that a declaration declares, such as {@code acme.dev/Project}: its names, its parents, its
 * relations and who may do what with its resources. It checks its own rules, and those that it shares with the other
 * types of its declaration.
 *
 * <p>
 * The relations hold what the declaration declares, without the relation {@code parent} that Pyrite adds to the
 * authorization model of a type with parents. Pyrite's own types have the plural that Pyrite gives them, {@code keys}
 * or {@code accounts}.
 *
 * @param type the type, as the declaration declares it
 * @param relations the relations, by name
 */
public record ResourceType(Type type, Map<String, Definition> relations) {

  private static final Pattern SINGULAR = Pattern.compile("[a-z][a-z0-9_]{0,49}");
  private static final Pattern PLURAL = Pattern.compile("[a-z][a-zA-Z0-9]{0,62}");

  public ResourceType {
    relations = Map.copyOf(relations);
  }

  /**
   * Reads a type of a declaration, and checks the rules that it keeps on its own: its names, and that each relation
   * has directly related types exactly when its rewrite takes subjects directly.
   *
   * @param type the type, as the declaration declares it, with the plural of Pyrite's own types
   * @return the type
   * @throws IllegalArgumentException when the type breaks a rule
   */
  static ResourceType of(Type type) {
    if (type.getName().isBlank()) {
      throw new IllegalArgumentException("A type has no name.");
    }
    String where = "The type " + type.getName();
    matches(SINGULAR, type.getDefinition().getType(), where + " has the singular name ");
    matches(PLURAL, type.getPlural(), where + " has the plural name ");
    Map<String, RelationMetadata> metadata = type.getDefinition().getMetadata().getRelationsMap();
    for (String relation : metadata.keySet()) {
      if (!type.getDefinition().getRelationsMap().containsKey(relation)) {
        throw new IllegalArgumentException(where + " has metadata for " + relation + ", which is not a relation.");
      }
    }
    Map<String, Definition> relations = new LinkedHashMap<>();
    type.getDefinition().getRelationsMap().forEach((name, rewrite) -> {
      matches(SINGULAR, name, where + " has the relation ");
      if (name.equals(TypeSystem.PARENT)) {
        throw new IllegalArgumentException(where + " declares the relation parent, which is Pyrite's: it holds the"
            + " resource that a resource is under.");
      }
      List<RelationReference> direct = metadata.getOrDefault(name, RelationMetadata.getDefaultInstance())
          .getDirectlyRelatedUserTypesList();
      if (direct.isEmpty() == RewriteVisitor.walk(rewrite, DIRECT)) {
        throw new IllegalArgumentException("The relation " + name + " of the type " + type.getName() + " has"
            + " directly related types exactly when its rewrite has this.");
      }
      relations.put(name, new Definition(rewrite, direct));
    });
    return new ResourceType(type, relations);
  }

  public String name() {
    return type.getName();
  }

  public String singular() {
    return type.getDefinition().getType();
  }

  public String plural() {
    return type.getPlural();
  }

  public List<String> parents() {
    return type.getParentsList();
  }

  public Permissions permissions() {
    return type.getPermissions();
  }

  public List<String> grantable() {
    return type.getGrantableList();
  }

  public String creator() {
    return type.getCreator();
  }

  /**
   * Gives the name of the resource of an id.
   *
   * @param id the id, such as {@code ledger}
   * @return the name, such as {@code projects/ledger}
   */
  public String resource(String id) {
    return plural() + "/" + id;
  }

  /**
   * Gives the id of the resource of a name.
   *
   * @param resource the name of a resource of the type, such as {@code projects/ledger}
   * @return the id, such as {@code ledger}
   */
  public String id(String resource) {
    return resource.substring(plural().length() + 1);
  }

  /**
   * Checks the rules that the type keeps with the other types of its declaration: that what it names is declared,
   * that its permissions and grants name relations that take what they need, and the relations of Pyrite's own types.
   *
   * @param types the types of the declaration
   * @throws IllegalArgumentException when the type breaks a rule
   */
  void check(TypeSystem types) {
    String where = "The type " + name();
    for (String parent : parents()) {
      if (types.type(parent).isEmpty()) {
        throw new IllegalArgumentException(where + " has the parent type " + parent + ", which is not declared.");
      }
    }
    relations.forEach((name, relation) -> {
      for (RelationReference reference : relation.direct()) {
        checkDirect(types, name, reference);
      }
      RewriteVisitor.walk(relation.rewrite(), rewriteCheck(types, name));
    });
    checkPermissions(types);
    if (name().equals(TypeSystem.KEY)) {
      requireRelations(TypeSystem.KEY_RELATIONS);
    }
    if (name().equals(TypeSystem.ACCOUNT)) {
      requireRelations(TypeSystem.ACCOUNT_RELATIONS);
      if (!takesUser(TypeSystem.OWNER)) {
        throw new IllegalArgumentException("The relation owner of " + TypeSystem.ACCOUNT + " takes a user directly;"
            + " Pyrite grants it to each account on itself.");
      }
    }
  }

  /**
   * Tells whether a rewrite takes subjects directly, through {@code this} anywhere in it.
   */
  private static final RewriteVisitor<Boolean> DIRECT = new RewriteVisitor<>() {

    @Override
    public Boolean direct() {
      return true;
    }

    @Override
    public Boolean computed(String relation) {
      return false;
    }

    @Override
    public Boolean tupleToUserset(String tupleset, String relation) {
      return false;
    }

    @Override
    public Boolean union(List<Boolean> children) {
      return children.contains(true);
    }

    @Override
    public Boolean intersection(List<Boolean> children) {
      return children.contains(true);
    }

    @Override
    public Boolean difference(Boolean base, Boolean subtract) {
      return base || subtract;
    }

    @Override
    public Boolean empty() {
      return false;
    }
  };

  /**
   * Checks that a rewrite names relations of the type, and of the types that it reads them from.
   */
  private RewriteVisitor<Void> rewriteCheck(TypeSystem types, String relation) {
    String where = "The relation " + relation + " of the type " + name();
    return new RewriteVisitor<>() {

      @Override
      public Void direct() {
        return null;
      }

      @Override
      public Void computed(String name) {
        if (!relations.containsKey(name)) {
          throw new IllegalArgumentException(where + " names " + name + ", which is not a relation of the type.");
        }
        return null;
      }

      @Override
      public Void tupleToUserset(String tupleset, String name) {
        List<Optional<ResourceType>> targets;
        if (tupleset.equals(TypeSystem.PARENT)) {
          if (parents().isEmpty()) {
            throw new IllegalArgumentException(where + " reads " + name + " from parent, but the type has no"
                + " parents.");
          }
          targets = types.parents(ResourceType.this).stream().map(Optional::of).toList();
        } else {
          Definition through = relations.get(tupleset);
          if (through == null || through.direct().isEmpty()) {
            throw new IllegalArgumentException(where + " reads " + name + " from " + tupleset + ", which is not"
                + " a relation of the type with direct types.");
          }
          targets = through.direct().stream().map(reference -> types.singular(reference.getType())).toList();
        }
        for (Optional<ResourceType> target : targets) {
          if (target.isEmpty() || !target.get().relations().containsKey(name)) {
            throw new IllegalArgumentException(where + " reads " + name + " from " + tupleset + ", but "
                + target.map(other -> "the type " + other.name()).orElse("the type user") + " has no relation "
                + name + ".");
          }
        }
        return null;
      }

      @Override
      public Void union(List<Void> children) {
        return null;
      }

      @Override
      public Void intersection(List<Void> children) {
        return null;
      }

      @Override
      public Void difference(Void base, Void subtract) {
        return null;
      }

      @Override
      public Void empty() {
        return null;
      }
    };
  }

  private void checkDirect(TypeSystem types, String relation, RelationReference reference) {
    String where = "The type " + name() + " grants " + relation;
    if (reference.getType().equals(TypeSystem.USER)) {
      if (!reference.getRelation().isEmpty()) {
        throw new IllegalArgumentException(where + " to user#" + reference.getRelation() + ", but the type user has"
            + " no relations.");
      }
    } else {
      ResourceType target = types.singular(reference.getType()).orElseThrow(() -> new IllegalArgumentException(
          where + " to the type " + reference.getType() + ", which is not declared."));
      if (!reference.getRelation().isEmpty() && !target.relations().containsKey(reference.getRelation())) {
        throw new IllegalArgumentException(where + " to " + reference.getType() + "#" + reference.getRelation()
            + ", which is not a relation of that type.");
      }
    }
    if (!reference.getCondition().isEmpty() && !types.conditions().containsKey(reference.getCondition())) {
      throw new IllegalArgumentException(where + " with the condition " + reference.getCondition() + ", which is not"
          + " declared.");
    }
  }

  private void checkPermissions(TypeSystem types) {
    String where = "The type " + name();
    String grant = permissions().getGrant();
    if (!relations.containsKey(grant)) {
      throw new IllegalArgumentException(where + " needs the relation " + grant + " for its grant permission, but"
          + " does not declare it.");
    }
    String create = permissions().getCreate();
    if (parents().isEmpty() && !create.isEmpty()) {
      throw new IllegalArgumentException(where + " has no parents, so its create permission is empty; anyone that"
          + " the caller names creates one.");
    }
    for (ResourceType parent : types.parents(this)) {
      if (!parent.relations().containsKey(create)) {
        throw new IllegalArgumentException(where + " is created with the relation " + create + " on its parent,"
            + " but the type " + parent.name() + " does not declare it.");
      }
    }
    for (String relation : grantable()) {
      Definition definition = relations.get(relation);
      if (definition == null || definition.direct().isEmpty()) {
        throw new IllegalArgumentException(where + " grants " + relation + ", which is not a relation of the type"
            + " with direct types.");
      }
    }
    if (!creator().isEmpty() && (!grantable().contains(creator()) || !takesUser(creator()))) {
      throw new IllegalArgumentException(where + " grants " + creator() + " to its creator, which has to be a"
          + " grantable relation that takes a user without a condition.");
    }
  }

  /**
   * Tells whether a relation takes a single user directly, without a wildcard or a condition.
   */
  private boolean takesUser(String relation) {
    return relations.get(relation).direct().stream().anyMatch(reference -> reference.getType().equals(
        TypeSystem.USER) && reference.getRelation().isEmpty() && !reference.hasWildcard()
        && reference.getCondition().isEmpty());
  }

  private void requireRelations(List<String> names) {
    for (String relation : names) {
      if (!relations.containsKey(relation)) {
        throw new IllegalArgumentException("The type " + name() + " lacks the relation " + relation + ", which"
            + " Pyrite checks.");
      }
    }
  }

  private static void matches(Pattern pattern, String value, String where) {
    if (!pattern.matcher(value).matches()) {
      throw new IllegalArgumentException(where + value + ", which does not match " + pattern.pattern() + ".");
    }
  }
}
