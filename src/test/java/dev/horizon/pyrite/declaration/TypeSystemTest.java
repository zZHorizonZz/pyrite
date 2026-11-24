package dev.horizon.pyrite.declaration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.TextFormat;
import dev.horizon.pyrite.schema.v1.Declaration;
import dev.horizon.spectrum.openfga.v1.RelationReference;
import dev.horizon.spectrum.openfga.v1.TypeDefinition;
import dev.horizon.spectrum.openfga.v1.Userset;
import dev.horizon.spectrum.openfga.v1.WriteAuthorizationModelRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TypeSystemTest {

  private static final String ORGANIZATION = """
      types {
        name: "acme.dev/Organization"
        definition {
          relations { key: "viewer" value { this {} } }
          metadata {
            relations { key: "viewer" value { directly_related_user_types { type: "pyrite.dev/Account" } } }
          }
        }
        permissions { grant: "viewer" }
      }
      """;

  @Test
  @DisplayName("A type with parents gets the relation parent, and keeps its relations that read from it")
  void parent() throws Exception {
    TypeSystem types = new TypeSystem(declaration(ORGANIZATION + """
        types {
          name: "acme.dev/Project" parents: "acme.dev/Organization"
          definition {
            relations { key: "owner" value { this {} } }
            relations {
              key: "viewer"
              value {
                union {
                  child { this {} }
                  child { computed_userset { relation: "owner" } }
                  child { tuple_to_userset { tupleset { relation: "parent" } computed_userset { relation: "viewer" } } }
                }
              }
            }
            metadata {
              relations { key: "owner" value { directly_related_user_types { type: "pyrite.dev/Account" } } }
              relations { key: "viewer" value { directly_related_user_types { type: "pyrite.dev/Account" } } }
            }
          }
          permissions { create: "viewer" grant: "viewer" }
        }
        """));
    WriteAuthorizationModelRequest model = types.model("store");
    TypeDefinition project = model.getTypeDefinitionsList().stream()
        .filter(definition -> definition.getType().equals("acme.dev/Project")).findFirst().orElseThrow();
    TypeDefinition organization = model.getTypeDefinitionsList().stream()
        .filter(definition -> definition.getType().equals("acme.dev/Organization")).findFirst().orElseThrow();

    assertThat(project.getRelationsMap().get("viewer").getUnion().getChildList()).extracting(Userset::getUsersetCase)
        .containsExactly(Userset.UsersetCase.THIS, Userset.UsersetCase.COMPUTED_USERSET,
            Userset.UsersetCase.TUPLE_TO_USERSET);
    assertThat(project.getRelationsMap().get("parent").getUsersetCase()).isEqualTo(Userset.UsersetCase.THIS);
    assertThat(project.getMetadata().getRelationsMap().get("parent").getDirectlyRelatedUserTypesList())
        .extracting(RelationReference::getType).containsExactly("acme.dev/Organization");
    assertThat(organization.getRelationsMap()).doesNotContainKey("parent");
    assertThat(model.getTypeDefinitionsList()).extracting(TypeDefinition::getType)
        .containsExactly("pyrite.dev/Account", "acme.dev/Organization", "acme.dev/Project");
  }

  @Test
  @DisplayName("A type and a relation are named in the model by the names that the declaration gives them")
  void names() throws Exception {
    String relation = "app.shopdemo.dev/organizations.getIamPolicy";
    TypeSystem types = new TypeSystem(declaration(ORGANIZATION.replace("acme.dev/Organization",
        "app.shopdemo.dev/Organization").replace("\"viewer\"", "\"" + relation + "\"")));

    TypeDefinition organization = types.model("store").getTypeDefinitions(1);

    assertThat(organization.getType()).isEqualTo("app.shopdemo.dev/Organization");
    assertThat(organization.getRelationsMap()).containsOnlyKeys(relation);
    assertThat(organization.getMetadata().getRelationsMap()).containsOnlyKeys(relation);
  }

  @Test
  @DisplayName("A resource's type is the part of its name before the first colon")
  void typeOf() throws Exception {
    TypeSystem types = new TypeSystem(declaration(ORGANIZATION));

    assertThat(types.typeOf("acme.dev/Organization:organizations/acme").map(ResourceType::name))
        .contains("acme.dev/Organization");
    assertThat(types.typeOf("acme.dev/Organization:organizations/acme.dev/projects/q1-2026").map(ResourceType::name))
        .contains("acme.dev/Organization");
    assertThat(types.typeOf("acme.dev/Organization:organizations/acme").map(type -> type.id(
        "acme.dev/Organization:organizations/acme"))).contains("organizations/acme");
    assertThat(types.typeOf("organizations/acme")).isEmpty();
    assertThat(types.typeOf("acme.dev/Folder:folders/docs")).isEmpty();
    assertThat(types.typeOf("acme.dev:Organization:organizations/acme")).isEmpty();
  }

  @Test
  @DisplayName("A declaration names only what it declares")
  void references() {
    assertThatThrownBy(() -> new TypeSystem(declaration(ORGANIZATION.replace("{ type: \"pyrite.dev/Account\" }",
        "{ type: \"team\" relation: \"member\" }")))).hasMessageContaining("team");
    assertThatThrownBy(() -> new TypeSystem(declaration(ORGANIZATION.replace("value { this {} }",
        "value { union { child { this {} } child { computed_userset { relation: \"admin\" } } } }"))))
        .hasMessageContaining("admin");
    assertThatThrownBy(() -> new TypeSystem(declaration(ORGANIZATION.replace("value { this {} }",
        "value { union { child { this {} } child { tuple_to_userset { tupleset { relation: \"parent\" }"
            + " computed_userset { relation: \"viewer\" } } } } }"))))
        .hasMessageContaining("no parents");
    assertThatThrownBy(() -> new TypeSystem(declaration(ORGANIZATION.replace("{ type: \"pyrite.dev/Account\" }",
        "{ type: \"pyrite.dev/Account\" condition: \"unknown\" }")))).hasMessageContaining("unknown");
    assertThatThrownBy(() -> new TypeSystem(declaration(ORGANIZATION.replace("grant: \"viewer\"",
        "grant: \"editor\"")))).hasMessageContaining("editor");
  }

  @Test
  @DisplayName("A type does not declare the relation parent, which is Pyrite's")
  void reserved() {
    assertThatThrownBy(() -> new TypeSystem(declaration(ORGANIZATION.replace("key: \"viewer\" value { this {} }",
        "key: \"parent\" value { this {} }").replace(
            "relations { key: \"viewer\" value {"
                + " directly_related_user_types",
            "relations { key: \"parent\" value { directly_related_user_types")
        .replace("grant: \"viewer\"", "grant: \"parent\"")))).hasMessageContaining("parent");
  }

  private static Declaration declaration(String text) throws TextFormat.ParseException {
    Declaration.Builder declaration = Declaration.newBuilder();
    TextFormat.merge(text, declaration);
    return declaration.build();
  }
}
