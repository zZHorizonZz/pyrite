package dev.horizon.pyrite.declaration;

import dev.horizon.pyrite.schema.v1.Declaration;
import dev.horizon.pyrite.schema.v1.ExchangeDefinition;
import dev.horizon.pyrite.schema.v1.IdentifierKind;
import dev.horizon.pyrite.schema.v1.Stage;
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

/**
 * A declaration, compiled: its types, their relations and hierarchy, its conditions and its exchanges, and the
 * authorization model that it gives OpenFGA.
 *
 * <p>
 * Compiling checks every rule of the declaration, and fails with {@link IllegalArgumentException} at the first rule
 * that it breaks. A type with parents gets the relation {@code parent} in the model, whose tuples Pyrite writes when a
 * resource is made known, so OpenFGA reads a relation from the parent itself.
 *
 * <pre>{@code
 * TypeSystem types = new TypeSystem(declaration);
 * Optional<ResourceType> project = types.typeOf("projects/ledger");
 * WriteAuthorizationModelRequest model = types.model(storeId);
 * }</pre>
 */
public final class TypeSystem {

  private static final Pattern EXCHANGE = Pattern.compile("[a-z][a-z0-9-]{0,62}");

  /** The singular name of {@code pyrite.dev/Account}, and the OpenFGA type of the subjects of calls. */
  public static final String USER = "user";
  /** The relation that holds the parent of a resource, which Pyrite adds to each type with parents. */
  public static final String PARENT = "parent";
  /** The name of Pyrite's type of keys. */
  public static final String KEY = "pyrite.dev/Key";
  /** The name of Pyrite's type of accounts. */
  public static final String ACCOUNT = "pyrite.dev/Account";
  /** The relation that each account holds on itself. */
  public static final String OWNER = "owner";
  /** The relations that the cryptography service checks on a key. */
  public static final List<String> KEY_RELATIONS = List.of("viewer", "admin", "encrypter", "decrypter", "signer", "verifier");
  /** The relations of an account: {@code owner}, which it holds on itself, and those that Pyrite checks on it. */
  public static final List<String> ACCOUNT_RELATIONS = List.of(OWNER, "viewer", "admin");

  private final Map<String, ResourceType> byName = new LinkedHashMap<>();
  private final Map<String, ResourceType> byPlural = new HashMap<>();
  private final Map<String, ResourceType> bySingular = new HashMap<>();
  private final Map<String, Condition> conditions = new LinkedHashMap<>();
  private final Map<String, ExchangeDefinition> exchanges = new LinkedHashMap<>();
  private final Map<String, IdentifierFormat> kinds = new LinkedHashMap<>();

  /**
   * Compiles a declaration, and checks its rules: those of its conditions, then of each type on its own, of its
   * identifier kinds, of each type with the others, and of its exchanges.
   *
   * @param declaration the declaration
   * @throws IllegalArgumentException at the first rule that the declaration breaks, with a message that names the
   * type, the relation, the kind or the exchange, and the rule
   */
  public TypeSystem(Declaration declaration) {
    declaration.getConditionsMap().forEach((name, condition) -> {
      if (!name.equals(condition.getName())) {
        throw new IllegalArgumentException("The condition " + name + " is named " + condition.getName() + ".");
      }
      conditions.put(name, condition);
    });
    for (Type type : expand(declaration)) {
      ResourceType compiled = ResourceType.of(type);
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
      if (kinds.put(kind.getName(), IdentifierFormat.of(kind)) != null) {
        throw new IllegalArgumentException("The identifier kind " + kind.getName() + " is declared twice.");
      }
    }
    for (ResourceType type : byName.values()) {
      type.check(this);
    }
    for (ExchangeDefinition exchange : declaration.getExchangesList()) {
      if (exchanges.put(exchange.getName(), exchange) != null) {
        throw new IllegalArgumentException("The exchange " + exchange.getName() + " is declared twice.");
      }
    }
    checkExchanges();
  }

  public Optional<ExchangeDefinition> exchange(String name) {
    return Optional.ofNullable(exchanges.get(name));
  }

  public List<ExchangeDefinition> exchanges() {
    return List.copyOf(exchanges.values());
  }

  public List<IdentifierFormat> identifierKinds() {
    return List.copyOf(kinds.values());
  }

  public Optional<IdentifierFormat> identifierKind(String name) {
    return Optional.ofNullable(kinds.get(name));
  }

  /**
   * Finds the kind of a value that names none.
   *
   * @param value the value as given, such as {@code Anne@Acme.dev}
   * @return the first kind, in the order of the declaration, whose {@code match} the value meets, or empty for none
   */
  public Optional<IdentifierFormat> identifierKindOf(String value) {
    return kinds.values().stream().filter(kind -> kind.matches(value)).findFirst();
  }

  /**
   * Finds a type by its name.
   *
   * @param name the name, such as {@code acme.dev/Project}
   * @return the type, or empty when the declaration has none of the name
   */
  public Optional<ResourceType> type(String name) {
    return Optional.ofNullable(byName.get(name));
  }

  /**
   * Finds a type by its singular name, the OpenFGA type of its objects.
   *
   * @param singular the singular name, such as {@code project}
   * @return the type, or empty when the declaration has none of the name, such as for {@code user} without
   * {@code pyrite.dev/Account}
   */
  public Optional<ResourceType> singular(String singular) {
    return Optional.ofNullable(bySingular.get(singular));
  }

  /**
   * Finds the type of a resource by the first segment of its name.
   *
   * @param resource the name of the resource, such as {@code projects/ledger}
   * @return the type whose plural the name starts with, or empty for a name without a slash or of no declared type
   */
  public Optional<ResourceType> typeOf(String resource) {
    int slash = resource.indexOf('/');
    return slash < 0 ? Optional.empty() : Optional.ofNullable(byPlural.get(resource.substring(0, slash)));
  }

  public List<ResourceType> types() {
    return List.copyOf(byName.values());
  }

  public Map<String, Condition> conditions() {
    return Map.copyOf(conditions);
  }

  /**
   * Gives the parent types of a type.
   *
   * @param type a type of this declaration
   * @return the parent types, in the order of the declaration; none for a type at the top
   */
  public List<ResourceType> parents(ResourceType type) {
    return type.parents().stream().map(byName::get).toList();
  }

  /**
   * Gives the authorization model of the declaration, to write to OpenFGA. It has each type with its relations, the
   * relation {@code parent} of each type with parents, the conditions, and a type {@code user} without relations when
   * the declaration has no {@code pyrite.dev/Account}.
   *
   * @param store the id of the OpenFGA store
   * @return the request that writes the model to the store, in schema version 1.1
   */
  public WriteAuthorizationModelRequest model(String store) {
    WriteAuthorizationModelRequest.Builder model = WriteAuthorizationModelRequest.newBuilder().setStoreId(store)
        .setSchemaVersion("1.1").putAllConditions(conditions);
    if (!byName.containsKey(ACCOUNT)) {
      model.addTypeDefinitions(TypeDefinition.newBuilder().setType(USER));
    }
    for (ResourceType type : byName.values()) {
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

  /**
   * Checks that each exchange of the declaration is named, has stages and completions, and has what each completion
   * needs: an account proven by its stages or its input, and the identifier kinds that it sends codes to.
   */
  private void checkExchanges() {
    if (!exchanges().isEmpty() && type(TypeSystem.ACCOUNT).isEmpty()) {
      throw new IllegalArgumentException(
          "The declaration has exchanges but no accounts; declare the type " + TypeSystem.ACCOUNT + ".");
    }
    for (ExchangeDefinition exchange : exchanges()) {
      String where = "The exchange " + exchange.getName();
      if (!EXCHANGE.matcher(exchange.getName()).matches()) {
        throw new IllegalArgumentException(where + " has a name that does not match " + EXCHANGE.pattern() + ".");
      }
      if (exchange.getInput() == ExchangeDefinition.Input.INPUT_UNSPECIFIED
          || exchange.getInput() == ExchangeDefinition.Input.UNRECOGNIZED
          || exchange.getCompletesList().contains(ExchangeDefinition.Completion.COMPLETION_UNSPECIFIED)
          || exchange.getCompletesList().contains(ExchangeDefinition.Completion.UNRECOGNIZED)) {
        throw new IllegalArgumentException(where + " has no input, or a completion with no kind.");
      }
      if (exchange.getEmailMatch() != ExchangeDefinition.EmailMatch.EMAIL_MATCH_UNSPECIFIED
          && exchange.getInput() != ExchangeDefinition.Input.NONE) {
        throw new IllegalArgumentException(where + " matches emails, which only an exchange of the input NONE does.");
      }
      if (exchange.getStagesList().isEmpty() || exchange.getCompletesList().isEmpty()) {
        throw new IllegalArgumentException(where + " has no stages or no completions.");
      }
      if ((exchange.getInput() == ExchangeDefinition.Input.IDENTIFIER
          || exchange.getInput() == ExchangeDefinition.Input.SUBJECT_IDENTIFIER) && identifierKinds().isEmpty()) {
        throw new IllegalArgumentException(where + " starts from an identifier, but the declaration has no identifier"
            + " kinds.");
      }
      if (exchange.getStagesList().stream().anyMatch(Stage::getIdentifier)
          && identifierKinds().stream().noneMatch(IdentifierFormat::receivesCodes)) {
        throw new IllegalArgumentException(where + " sends a code to an identifier, but no identifier kind of the"
            + " declaration receives codes.");
      }
      boolean identified = exchange.getInput() == ExchangeDefinition.Input.SUBJECT
          || exchange.getInput() == ExchangeDefinition.Input.SUBJECT_IDENTIFIER;
      boolean factors = false;
      boolean identifier = false;
      boolean enrols = false;
      for (Stage stage : exchange.getStagesList()) {
        if (stage.getFactors() < 0 || stage.getFactors() > 3) {
          throw new IllegalArgumentException(where + " requires " + stage.getFactors() + " factors; a stage requires"
              + " 0 to 3.");
        }
        if (stage.getEnrol() != Stage.Factor.FACTOR_UNSPECIFIED
            && (stage.getFactors() > 0 || stage.getPhishingResistant() || stage.getIdentifier())) {
          throw new IllegalArgumentException(where + " enrols a factor in a stage that requires more; an enrolment"
              + " is a stage of its own.");
        }
        if (stage.getFactors() == 0 && !stage.getPhishingResistant() && !stage.getIdentifier()
            && stage.getEnrol() == Stage.Factor.FACTOR_UNSPECIFIED) {
          throw new IllegalArgumentException(where + " has a stage that requires nothing.");
        }
        if (stage.getEnrol() != Stage.Factor.FACTOR_UNSPECIFIED && (enrols || !identified)) {
          throw new IllegalArgumentException(where + " enrols a factor twice, or before the account is proven by the"
              + " input SUBJECT or by an earlier stage.");
        }
        factors |= stage.getFactors() > 0 || stage.getPhishingResistant();
        identifier |= stage.getIdentifier();
        enrols |= stage.getEnrol() != Stage.Factor.FACTOR_UNSPECIFIED;
        identified |= factors || identifier;
      }
      for (ExchangeDefinition.Completion completion : exchange.getCompletesList()) {
        boolean proven = switch (completion) {
          case ISSUE_SESSION -> factors && (exchange.getInput() == ExchangeDefinition.Input.IDENTIFIER
              || exchange.getInput() == ExchangeDefinition.Input.NONE);
          case CREATE_ACCOUNT -> factors && exchange.getInput() == ExchangeDefinition.Input.NONE;
          case MARK_VERIFIED -> identifier && exchange.getInput() == ExchangeDefinition.Input.SUBJECT_IDENTIFIER;
          case ACTIVATE_FACTOR -> enrols;
          case END_SESSIONS -> identified;
          case COMPLETION_UNSPECIFIED, UNRECOGNIZED -> false;
        };
        if (!proven) {
          throw new IllegalArgumentException(where + " completes with " + completion + " without the stages and the"
              + " input that it needs.");
        }
      }
    }
  }
}
