package dev.horizon.pyrite.impl.model;

import dev.horizon.pyrite.schema.v1.FlowDefinition;
import dev.horizon.pyrite.schema.v1.Stage;
import dev.horizon.spectrum.openfga.v1.RelationReference;
import dev.horizon.spectrum.openfga.v1.Userset;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

final class DeclarationValidator {

  private static final Pattern FLOW = Pattern.compile("[a-z][a-z0-9-]{0,62}");

  private final TypeSystem types;

  DeclarationValidator(TypeSystem types) {
    this.types = types;
  }

  void references() {
    for (DeclaredType type : types.types()) {
      String where = "The type " + type.name();
      for (String parent : type.parents()) {
        if (types.type(parent).isEmpty()) {
          throw new IllegalArgumentException(where + " has the parent type " + parent + ", which is not declared.");
        }
      }
      type.relations().forEach((name, relation) -> {
        for (RelationReference reference : relation.direct()) {
          direct(type, name, reference);
        }
        rewrites(type, name, relation.rewrite());
      });
    }
  }

  void permissions() {
    for (DeclaredType type : types.types()) {
      DeclaredType declared = type;
      String where = "The type " + declared.name();
      String grant = declared.permissions().getGrant();
      if (!type.relations().containsKey(grant)) {
        throw new IllegalArgumentException(where + " needs the relation " + grant + " for its grant permission, but"
            + " does not declare it.");
      }
      String create = declared.permissions().getCreate();
      if (declared.parents().isEmpty() && !create.isEmpty()) {
        throw new IllegalArgumentException(where + " has no parents, so its create permission is empty; anyone that"
            + " the caller names creates one.");
      }
      for (DeclaredType parent : types.parents(type)) {
        if (!parent.relations().containsKey(create)) {
          throw new IllegalArgumentException(where + " is created with the relation " + create + " on its parent,"
              + " but the type " + parent.name() + " does not declare it.");
        }
      }
      for (String relation : declared.grantable()) {
        Definition definition = type.relations().get(relation);
        if (definition == null || definition.direct().isEmpty()) {
          throw new IllegalArgumentException(where + " grants " + relation + ", which is not a relation of the type"
              + " with direct types.");
        }
      }
      if (!declared.creator().isEmpty() && (!declared.grantable().contains(declared.creator())
          || type.relations().get(declared.creator()).direct().stream()
              .noneMatch(reference -> reference.getType().equals(TypeSystem.USER)
                  && reference.getRelation().isEmpty() && !reference.hasWildcard()
                  && reference.getCondition().isEmpty()))) {
        throw new IllegalArgumentException(where + " grants " + declared.creator() + " to its creator, which has to"
            + " be a grantable relation that takes a user without a condition.");
      }
      if (declared.name().equals(TypeSystem.KEY)) {
        own(type, TypeSystem.KEY_RELATIONS);
      }
      if (declared.name().equals(TypeSystem.ACCOUNT)) {
        own(type, TypeSystem.ACCOUNT_RELATIONS);
        owner(type);
      }
    }
  }

  void flows() {
    if (!types.flows().isEmpty() && types.type(TypeSystem.ACCOUNT).isEmpty()) {
      throw new IllegalArgumentException(
          "The declaration has flows but no accounts; declare the type " + TypeSystem.ACCOUNT + ".");
    }
    for (FlowDefinition flow : types.flows()) {
      String where = "The flow " + flow.getName();
      if (!FLOW.matcher(flow.getName()).matches()) {
        throw new IllegalArgumentException(where + " has a name that does not match " + FLOW.pattern() + ".");
      }
      if (flow.getInput() == FlowDefinition.Input.INPUT_UNSPECIFIED
          || flow.getInput() == FlowDefinition.Input.UNRECOGNIZED
          || flow.getCompletesList().contains(FlowDefinition.Completion.COMPLETION_UNSPECIFIED)
          || flow.getCompletesList().contains(FlowDefinition.Completion.UNRECOGNIZED)) {
        throw new IllegalArgumentException(where + " has no input, or a completion with no kind.");
      }
      if (flow.getStagesList().isEmpty() || flow.getCompletesList().isEmpty()) {
        throw new IllegalArgumentException(where + " has no stages or no completions.");
      }
      if (flow.getInput() != FlowDefinition.Input.SUBJECT && types.identifierKinds().isEmpty()) {
        throw new IllegalArgumentException(where + " starts from an identifier, but the declaration has no identifier"
            + " kinds.");
      }
      if (flow.getStagesList().stream().anyMatch(Stage::getIdentifier)
          && types.identifierKinds().stream().noneMatch(DeclaredKind::receivesCodes)) {
        throw new IllegalArgumentException(where + " sends a code to an identifier, but no identifier kind of the"
            + " declaration receives codes.");
      }
      boolean identified = flow.getInput() != FlowDefinition.Input.IDENTIFIER;
      boolean factors = false;
      boolean identifier = false;
      boolean enrols = false;
      for (Stage stage : flow.getStagesList()) {
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
      for (FlowDefinition.Completion completion : flow.getCompletesList()) {
        boolean proven = switch (completion) {
          case ISSUE_SESSION -> factors && flow.getInput() == FlowDefinition.Input.IDENTIFIER;
          case MARK_VERIFIED -> identifier && flow.getInput() == FlowDefinition.Input.SUBJECT_IDENTIFIER;
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

  private void direct(DeclaredType type, String relation, RelationReference reference) {
    String where = "The type " + type.name() + " grants " + relation;
    if (reference.getType().equals(TypeSystem.USER)) {
      if (!reference.getRelation().isEmpty()) {
        throw new IllegalArgumentException(where + " to user#" + reference.getRelation() + ", but the type user has"
            + " no relations.");
      }
    } else {
      DeclaredType target = types.singular(reference.getType()).orElseThrow(() -> new IllegalArgumentException(
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

  private void rewrites(DeclaredType type, String relation, Userset userset) {
    String where = "The relation " + relation + " of the type " + type.name();
    switch (userset.getUsersetCase()) {
      case COMPUTED_USERSET -> {
        if (!type.relations().containsKey(userset.getComputedUserset().getRelation())) {
          throw new IllegalArgumentException(where + " names " + userset.getComputedUserset().getRelation()
              + ", which is not a relation of the type.");
        }
      }
      case TUPLE_TO_USERSET -> {
        String tupleset = userset.getTupleToUserset().getTupleset().getRelation();
        String computed = userset.getTupleToUserset().getComputedUserset().getRelation();
        List<Optional<DeclaredType>> targets;
        if (tupleset.equals(TypeSystem.PARENT)) {
          if (type.parents().isEmpty()) {
            throw new IllegalArgumentException(where + " reads " + computed + " from parent, but the type has no"
                + " parents.");
          }
          targets = types.parents(type).stream().map(Optional::of).toList();
        } else {
          Definition through = type.relations().get(tupleset);
          if (through == null || through.direct().isEmpty()) {
            throw new IllegalArgumentException(where + " reads " + computed + " from " + tupleset + ", which is not a"
                + " relation of the type with direct types.");
          }
          targets = through.direct().stream().map(reference -> types.singular(reference.getType())).toList();
        }
        for (Optional<DeclaredType> target : targets) {
          if (target.isEmpty() || !target.get().relations().containsKey(computed)) {
            throw new IllegalArgumentException(where + " reads " + computed + " from " + tupleset + ", but "
                + target.map(found -> "the type " + found.name()).orElse("the type user") + " has no relation "
                + computed + ".");
          }
        }
      }
      case UNION -> userset.getUnion().getChildList().forEach(child -> rewrites(type, relation, child));
      case INTERSECTION -> userset.getIntersection().getChildList().forEach(child -> rewrites(type, relation, child));
      case DIFFERENCE -> {
        rewrites(type, relation, userset.getDifference().getBase());
        rewrites(type, relation, userset.getDifference().getSubtract());
      }
      default -> {
      }
    }
  }

  private static void own(DeclaredType type, List<String> relations) {
    for (String relation : relations) {
      if (!type.relations().containsKey(relation)) {
        throw new IllegalArgumentException("The type " + type.name() + " lacks the relation " + relation + ", which"
            + " Pyrite checks.");
      }
    }
  }

  private static void owner(DeclaredType account) {
    if (account.relations().get(TypeSystem.OWNER).direct().stream().noneMatch(reference -> reference.getType()
        .equals(TypeSystem.USER) && reference.getRelation().isEmpty() && !reference.hasWildcard()
        && reference.getCondition().isEmpty())) {
      throw new IllegalArgumentException("The relation owner of " + TypeSystem.ACCOUNT + " takes a user directly;"
          + " Pyrite grants it to each account on itself.");
    }
  }
}
