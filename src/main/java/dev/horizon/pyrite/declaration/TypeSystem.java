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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A declaration, compiled: its types, their relations and hierarchy, its conditions and its exchanges, and the
 * authorization model that it writes to the store of grants.
 *
 * <p>
 * Compiling checks every rule of the declaration, and fails with {@link IllegalArgumentException} at the first rule
 * that it breaks. A type with parents gets the relation {@code parent} in the model, whose tuples Pyrite writes when a
 * resource is made known, so that a check reads a relation from the parent itself.
 *
 * <pre>{@code
 * TypeSystem types = new TypeSystem(declaration);
 * Optional<ResourceType> project = types.typeOf("acme.dev/Project:organizations/acme/projects/ledger");
 * WriteAuthorizationModelRequest model = types.model(storeId);
 * }</pre>
 */
public final class TypeSystem {

  private static final Pattern EXCHANGE = Pattern.compile("[a-z][a-z0-9-]{0,62}");

  /**
   * The names of types, as OpenFGA takes them: 1 to 254 characters, without {@code :}, {@code #}, {@code @} or white
   * space. A type's name is also its type in the authorization model.
   */
  public static final Pattern TYPE_NAME = Pattern.compile("[^:#@\\s]{1,254}");
  /**
   * The names of relations, as OpenFGA takes them: 1 to 50 characters, without {@code :}, {@code #}, {@code @} or
   * white space.
   */
  public static final Pattern RELATION_NAME = Pattern.compile("[^:#@\\s]{1,50}");
  /** The relation that holds the parent of a resource, which Pyrite adds to each type with parents. */
  public static final String PARENT = "parent";
  /** The name of Pyrite's type of keys. */
  public static final String KEY = "pyrite.dev/Key";
  /**
   * The name of Pyrite's type of accounts, and the type of the subjects of calls that are accounts, such as
   * {@code pyrite.dev/Account:accounts/anne}. The authorization model has it even when the declaration does not
   * declare it.
   */
  public static final String ACCOUNT = "pyrite.dev/Account";
  /**
   * The ids of keys and accounts, the last segments of their names, such as {@code payments} of
   * {@code keys/payments}.
   */
  public static final Pattern OWN_ID = Pattern.compile("[a-z]([a-z0-9-]{0,61}[a-z0-9])?");
  /** The relation that each account holds on itself. */
  public static final String OWNER = "owner";
  /** The relations that the cryptography service checks on a key. */
  public static final List<String> KEY_RELATIONS = List.of("viewer", "admin", "encrypter", "decrypter", "signer", "verifier");
  /** The relations of an account: {@code owner}, which it holds on itself, and those that Pyrite checks on it. */
  public static final List<String> ACCOUNT_RELATIONS = List.of(OWNER, "viewer", "admin");

  private final Map<String, ResourceType> byName = new LinkedHashMap<>();
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
    for (Type definition : declaration.getTypesList()) {
      String name = definition.getName();
      if (name.startsWith("pyrite.dev/") && !name.equals(KEY) && !name.equals(ACCOUNT)) {
        throw new IllegalArgumentException("The type " + name + " is Pyrite's; a declaration does not declare it.");
      }
      ResourceType type = ResourceType.of(definition);
      if (byName.put(type.name(), type) != null) {
        throw new IllegalArgumentException("The type " + type.name() + " is declared twice.");
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
   * Finds the type of a resource by its name, whose part before the first {@code :} names the type.
   *
   * @param resource the name of the resource, such as {@code acme.dev/Project:organizations/acme/projects/ledger}
   * @return the type, such as {@code acme.dev/Project}, or empty for a name without a {@code :} or of no declared type
   */
  public Optional<ResourceType> typeOf(String resource) {
    int colon = resource.indexOf(':');
    return colon < 0 ? Optional.empty() : type(resource.substring(0, colon));
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
   * Gives the authorization model of the declaration, to write to the store of grants. It has each type, named by its
   * name, with its relations, the relation {@code parent} of each type with parents, the conditions, and the type
   * {@code pyrite.dev/Account} without relations when the declaration does not declare it.
   *
   * @param store the id of the store of grants
   * @return the request that writes the model to the store, in schema version 1.1
   */
  public WriteAuthorizationModelRequest model(String store) {
    WriteAuthorizationModelRequest.Builder model = WriteAuthorizationModelRequest.newBuilder().setStoreId(store)
        .setSchemaVersion("1.1").putAllConditions(conditions);
    if (!byName.containsKey(ACCOUNT)) {
      model.addTypeDefinitions(TypeDefinition.newBuilder().setType(ACCOUNT));
    }
    for (ResourceType type : byName.values()) {
      TypeDefinition.Builder definition = TypeDefinition.newBuilder().setType(type.name());
      Metadata.Builder metadata = Metadata.newBuilder();
      type.relations().forEach((name, relation) -> {
        definition.putRelations(name, relation.rewrite());
        metadata.putRelations(name, RelationMetadata.newBuilder()
            .addAllDirectlyRelatedUserTypes(relation.direct()).build());
      });
      if (!type.parents().isEmpty()) {
        definition.putRelations(PARENT, Userset.newBuilder().setThis(DirectUserset.getDefaultInstance()).build());
        metadata.putRelations(PARENT, RelationMetadata.newBuilder().addAllDirectlyRelatedUserTypes(parents(type)
            .stream().map(parent -> RelationReference.newBuilder().setType(parent.name()).build()).toList())
            .build());
      }
      model.addTypeDefinitions(definition.setMetadata(metadata));
    }
    return model.build();
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
      boolean account = exchange.getInput() == ExchangeDefinition.Input.SUBJECT
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
        if (stage.getEnrol() != Stage.Factor.FACTOR_UNSPECIFIED && (enrols || !account)) {
          throw new IllegalArgumentException(where + " enrols a factor twice, or before the account is proven by the"
              + " input SUBJECT or by an earlier stage.");
        }
        factors |= stage.getFactors() > 0 || stage.getPhishingResistant();
        identifier |= stage.getIdentifier();
        enrols |= stage.getEnrol() != Stage.Factor.FACTOR_UNSPECIFIED;
        account |= factors || identifier;
      }
      for (ExchangeDefinition.Completion completion : exchange.getCompletesList()) {
        boolean possible = switch (completion) {
          case ISSUE_SESSION -> factors && (exchange.getInput() == ExchangeDefinition.Input.IDENTIFIER
              || exchange.getInput() == ExchangeDefinition.Input.NONE);
          case CREATE_ACCOUNT -> factors && exchange.getInput() == ExchangeDefinition.Input.NONE;
          case MARK_VERIFIED -> identifier && exchange.getInput() == ExchangeDefinition.Input.SUBJECT_IDENTIFIER;
          case ACTIVATE_FACTOR -> enrols;
          case END_SESSIONS -> account;
          case COMPLETION_UNSPECIFIED, UNRECOGNIZED -> false;
        };
        if (!possible) {
          throw new IllegalArgumentException(where + " completes with " + completion + " without the stages and the"
              + " input that it needs.");
        }
      }
    }
  }
}
