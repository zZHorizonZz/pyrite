package dev.horizon.pyrite.impl.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.TextFormat;
import dev.horizon.pyrite.schema.v1.Declaration;
import dev.horizon.pyrite.schema.v1.Field;
import dev.horizon.pyrite.schema.v1.FlowDefinition;
import dev.horizon.pyrite.schema.v1.Stage;
import dev.horizon.pyrite.schema.v1.Type;
import dev.horizon.spectrum.openfga.v1.Condition;
import dev.horizon.spectrum.openfga.v1.DirectUserset;
import dev.horizon.spectrum.openfga.v1.ObjectRelation;
import dev.horizon.spectrum.openfga.v1.RelationMetadata;
import dev.horizon.spectrum.openfga.v1.RelationReference;
import dev.horizon.spectrum.openfga.v1.TupleToUserset;
import dev.horizon.spectrum.openfga.v1.Userset;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DeclarationRulesTest {

  private static Declaration acme() throws Exception {
    Declaration.Builder declaration = Declaration.newBuilder();
    try (InputStream text = DeclarationRulesTest.class.getResourceAsStream("/acme/declaration.textproto")) {
      TextFormat.merge(new String(text.readAllBytes(), StandardCharsets.UTF_8), declaration);
    }
    return declaration.build();
  }

  private static Type.Builder type(Declaration.Builder declaration, String name) {
    for (int index = 0; index < declaration.getTypesCount(); index++) {
      if (declaration.getTypes(index).getName().equals(name)) {
        return declaration.getTypesBuilder(index);
      }
    }
    throw new AssertionError("No type " + name);
  }

  private static Type.Builder project(Declaration.Builder declaration) {
    return type(declaration, "acme.dev/Project");
  }

  private static Userset direct() {
    return Userset.newBuilder().setThis(DirectUserset.getDefaultInstance()).build();
  }

  private static Userset computed(String relation) {
    return Userset.newBuilder().setComputedUserset(ObjectRelation.newBuilder().setRelation(relation)).build();
  }

  private static Userset through(String tupleset, String relation) {
    return Userset.newBuilder().setTupleToUserset(TupleToUserset.newBuilder().setTupleset(ObjectRelation
        .newBuilder().setRelation(tupleset)).setComputedUserset(ObjectRelation.newBuilder().setRelation(relation)))
        .build();
  }

  private static RelationMetadata takes(String type, String relation) {
    return RelationMetadata.newBuilder().addDirectlyRelatedUserTypes(RelationReference.newBuilder().setType(type)
        .setRelation(relation)).build();
  }

  private static Arguments rule(String message, Consumer<Declaration.Builder> breaking) {
    return Arguments.of(message, breaking);
  }

  private static void broken(Consumer<Declaration.Builder> breaking, String message) throws Exception {
    Declaration.Builder declaration = acme().toBuilder();
    breaking.accept(declaration);
    assertThatThrownBy(() -> new TypeSystemImpl(declaration.build())).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(message);
  }

  @Test
  @DisplayName("the Acme declaration keeps every rule")
  void valid() throws Exception {
    Declaration acme = acme();

    assertThatCode(() -> new TypeSystemImpl(acme)).doesNotThrowAnyException();
  }

  @Nested
  @DisplayName("Types")
  class Types {

    static Stream<Arguments> rules() {
      return Stream.of(
          rule("is declared twice", declaration -> declaration.addTypes(project(declaration).build())),
          rule("A type has no name", declaration -> project(declaration).setName("")),
          rule("is Pyrite's; a declaration does not declare it", declaration -> project(declaration)
              .setName("pyrite.dev/Project")),
          rule("leaves plural, table and fields empty", declaration -> type(declaration, "pyrite.dev/Key")
              .setPlural("keys")),
          rule("has the singular name", declaration -> project(declaration).getDefinitionBuilder()
              .setType("Project")),
          rule("is Pyrite's accounts'", declaration -> project(declaration).getDefinitionBuilder().setType("user")),
          rule("which another type has", declaration -> project(declaration).getDefinitionBuilder()
              .setType("group")),
          rule("has the plural name", declaration -> project(declaration).setPlural("pro-jects")),
          rule("has the plural name groups", declaration -> project(declaration).setPlural("groups")),
          rule("has no table", declaration -> project(declaration).setTable("")),
          rule("has a negative retention", declaration -> project(declaration).getRetentionBuilder()
              .setSeconds(-1)),
          rule("has the field Budget", declaration -> project(declaration).addFields(Field.newBuilder()
              .setName("Budget").setKind(Field.Kind.INTEGER))),
          rule("with no kind", declaration -> project(declaration).addFields(Field.newBuilder().setName("budget"))),
          rule("twice", declaration -> project(declaration).addFields(Field.newBuilder().setName("billing_account")
              .setKind(Field.Kind.STRING))),
          rule("lacks the standard field purge_time", declaration -> project(declaration).removeFields(9)),
          rule("lacks the standard field labels", declaration -> project(declaration).getFieldsBuilder(4)
              .setKind(Field.Kind.STRING)),
          rule("has the parent type acme.dev/Folder", declaration -> project(declaration).setParents(0,
              "acme.dev/Folder")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules")
    @DisplayName("a type that breaks a rule is refused")
    void refused(String message, Consumer<Declaration.Builder> breaking) throws Exception {
      broken(breaking, message);
    }
  }

  @Nested
  @DisplayName("Relations")
  class Relations {

    static Stream<Arguments> rules() {
      return Stream.of(
          rule("has metadata for", declaration -> project(declaration).getDefinitionBuilder().getMetadataBuilder()
              .putRelations("auditor", takes("user", ""))),
          rule("declares the relation parent", declaration -> project(declaration).getDefinitionBuilder()
              .putRelations("parent", direct())),
          rule("has the relation ", declaration -> project(declaration).getDefinitionBuilder()
              .putRelations("Auditor", computed("owner"))),
          rule("exactly when its rewrite has this", declaration -> project(declaration).getDefinitionBuilder()
              .putRelations("auditor", direct())),
          rule("exactly when its rewrite has this", declaration -> {
            project(declaration).getDefinitionBuilder().putRelations("auditor", computed("owner"));
            project(declaration).getDefinitionBuilder().getMetadataBuilder().putRelations("auditor", takes("user",
                ""));
          }),
          rule("which is not declared", declaration -> {
            project(declaration).getDefinitionBuilder().putRelations("auditor", direct());
            project(declaration).getDefinitionBuilder().getMetadataBuilder().putRelations("auditor", takes(
                "folder", ""));
          }),
          rule("which is not a relation of that type", declaration -> {
            project(declaration).getDefinitionBuilder().putRelations("auditor", direct());
            project(declaration).getDefinitionBuilder().getMetadataBuilder().putRelations("auditor", takes("group",
                "owner"));
          }),
          rule("the type user has no relations", declaration -> {
            project(declaration).getDefinitionBuilder().putRelations("auditor", direct());
            project(declaration).getDefinitionBuilder().getMetadataBuilder().putRelations("auditor", takes("user",
                "member"));
          }),
          rule("with the condition", declaration -> {
            project(declaration).getDefinitionBuilder().putRelations("auditor", direct());
            project(declaration).getDefinitionBuilder().getMetadataBuilder().putRelations("auditor",
                RelationMetadata.newBuilder().addDirectlyRelatedUserTypes(RelationReference.newBuilder()
                    .setType("user").setCondition("on_weekdays")).build());
          }),
          rule("which is not a relation of the type", declaration -> project(declaration).getDefinitionBuilder()
              .putRelations("auditor", computed("accountant"))),
          rule("but the type has no parents", declaration -> type(declaration, "acme.dev/Organization")
              .getDefinitionBuilder().putRelations("auditor", through("parent", "admin"))),
          rule("has no relation auditor", declaration -> project(declaration).getDefinitionBuilder()
              .putRelations("reader", through("parent", "auditor"))),
          rule("with direct types", declaration -> project(declaration).getDefinitionBuilder()
              .putRelations("reader", through("can_view", "member"))),
          rule("is named", declaration -> declaration.putConditions("not_expired", declaration.getConditionsMap()
              .get("not_expired").toBuilder().setName("expired").build())),
          rule("which reads from parent", declaration -> {
            project(declaration).getDefinitionBuilder().putRelations("auditor", direct());
            project(declaration).getDefinitionBuilder().getMetadataBuilder().putRelations("auditor", takes(
                "project", "viewer"));
          }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules")
    @DisplayName("a relation that breaks a rule is refused")
    void refused(String message, Consumer<Declaration.Builder> breaking) throws Exception {
      broken(breaking, message);
    }
  }

  @Nested
  @DisplayName("Permissions")
  class Permissions {

    static Stream<Arguments> rules() {
      return Stream.of(
          rule("needs the relation can_read", declaration -> project(declaration).getPermissionsBuilder()
              .setRead("can_read")),
          rule("needs the relation can_grant", declaration -> project(declaration).getPermissionsBuilder()
              .setGrant("can_grant")),
          rule("so its create permission is empty", declaration -> type(declaration, "acme.dev/Organization")
              .getPermissionsBuilder().setCreate("admin")),
          rule("is created with the relation", declaration -> project(declaration).getPermissionsBuilder()
              .setCreate("owner")),
          rule("grants can_view, which is not a relation of the type with direct types",
              declaration -> project(declaration).addGrantable("can_view")),
          rule("grants auditor, which is not a relation", declaration -> project(declaration)
              .addGrantable("auditor")),
          rule("to its creator", declaration -> project(declaration).setCreator("can_view")),
          rule("to its creator", declaration -> project(declaration).setCreator("binding")),
          rule("lacks the relation verifier", declaration -> {
            type(declaration, "pyrite.dev/Key").getDefinitionBuilder().removeRelations("verifier");
            type(declaration, "pyrite.dev/Key").getDefinitionBuilder().getMetadataBuilder()
                .removeRelations("verifier");
            Type.Builder key = type(declaration, "pyrite.dev/Key");
            List<String> kept = new ArrayList<>(key.getGrantableList());
            kept.remove("verifier");
            key.clearGrantable().addAllGrantable(kept);
          }),
          rule("takes a user directly", declaration -> type(declaration, "pyrite.dev/Account")
              .getDefinitionBuilder().getMetadataBuilder().putRelations("owner", takes("group", "member"))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules")
    @DisplayName("permissions that break a rule are refused")
    void refused(String message, Consumer<Declaration.Builder> breaking) throws Exception {
      broken(breaking, message);
    }
  }

  @Nested
  @DisplayName("Flows")
  class Flows {

    private static FlowDefinition.Builder flow(String name, FlowDefinition.Input input,
        FlowDefinition.Completion completion, Stage... stages) {
      FlowDefinition.Builder flow = FlowDefinition.newBuilder().setName(name).setInput(input).addCompletes(
          completion);
      for (Stage stage : stages) {
        flow.addStages(stage);
      }
      return flow;
    }

    static Stream<Arguments> rules() {
      Stage password = Stage.newBuilder().setFactors(1).build();
      return Stream.of(
          rule("has flows but no accounts", declaration -> declaration.removeTypes(declaration.getTypesCount() - 1)),
          rule("does not match", declaration -> declaration.addFlows(flow("Sign_In", FlowDefinition.Input.IDENTIFIER,
              FlowDefinition.Completion.ISSUE_SESSION, password))),
          rule("is declared twice", declaration -> declaration.addFlows(declaration.getFlows(0))),
          rule("has no input", declaration -> declaration.addFlows(flow("odd",
              FlowDefinition.Input.INPUT_UNSPECIFIED, FlowDefinition.Completion.ISSUE_SESSION, password))),
          rule("has no stages or no completions", declaration -> declaration.addFlows(flow("empty",
              FlowDefinition.Input.IDENTIFIER, FlowDefinition.Completion.ISSUE_SESSION))),
          rule("a stage requires 0 to 3", declaration -> declaration.addFlows(flow("many",
              FlowDefinition.Input.IDENTIFIER, FlowDefinition.Completion.ISSUE_SESSION, Stage.newBuilder()
                  .setFactors(4).build()))),
          rule("an enrolment is a stage of its own", declaration -> declaration.addFlows(flow("mixed",
              FlowDefinition.Input.SUBJECT, FlowDefinition.Completion.ACTIVATE_FACTOR, Stage.newBuilder()
                  .setFactors(1).setEnrol(Stage.Factor.TOTP).build()))),
          rule("has a stage that requires nothing", declaration -> declaration.addFlows(flow("nothing",
              FlowDefinition.Input.IDENTIFIER, FlowDefinition.Completion.ISSUE_SESSION, password,
              Stage.getDefaultInstance()))),
          rule("before the account is proven", declaration -> declaration.addFlows(flow("takeover",
              FlowDefinition.Input.IDENTIFIER, FlowDefinition.Completion.ACTIVATE_FACTOR, Stage.newBuilder()
                  .setEnrol(Stage.Factor.PASSWORD).build()))),
          rule("enrols a factor twice", declaration -> declaration.addFlows(flow("twice",
              FlowDefinition.Input.SUBJECT, FlowDefinition.Completion.ACTIVATE_FACTOR, Stage.newBuilder()
                  .setEnrol(Stage.Factor.PASSWORD).build(),
              Stage.newBuilder().setEnrol(Stage.Factor.TOTP)
                  .build()))),
          rule("completes with ISSUE_SESSION", declaration -> declaration.addFlows(flow("session",
              FlowDefinition.Input.SUBJECT, FlowDefinition.Completion.ISSUE_SESSION, password))),
          rule("completes with ISSUE_SESSION", declaration -> declaration.addFlows(flow("codeOnly".toLowerCase(),
              FlowDefinition.Input.IDENTIFIER, FlowDefinition.Completion.ISSUE_SESSION, Stage.newBuilder()
                  .setIdentifier(true).build()))),
          rule("completes with MARK_VERIFIED", declaration -> declaration.addFlows(flow("verified",
              FlowDefinition.Input.IDENTIFIER, FlowDefinition.Completion.MARK_VERIFIED, Stage.newBuilder()
                  .setIdentifier(true).build()))),
          rule("completes with ACTIVATE_FACTOR", declaration -> declaration.addFlows(flow("activate",
              FlowDefinition.Input.SUBJECT, FlowDefinition.Completion.ACTIVATE_FACTOR, password))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules")
    @DisplayName("a flow that breaks a rule is refused")
    void refused(String message, Consumer<Declaration.Builder> breaking) throws Exception {
      broken(breaking, message);
    }

    @Test
    @DisplayName("a declaration without accounts and flows needs no accounts")
    void noAccounts() throws Exception {
      Declaration.Builder declaration = acme().toBuilder().clearFlows();
      declaration.removeTypes(declaration.getTypesCount() - 1);

      assertThatCode(() -> new TypeSystemImpl(declaration.build())).doesNotThrowAnyException();
    }
  }

  @Test
  @DisplayName("a condition that a declaration names but does not declare is refused")
  void condition() throws Exception {
    broken(declaration -> declaration.removeConditions("not_expired").putConditions("on_weekdays", Condition
        .newBuilder().setName("on_weekdays").setExpression("true").build()), "with the condition not_expired");
  }
}
