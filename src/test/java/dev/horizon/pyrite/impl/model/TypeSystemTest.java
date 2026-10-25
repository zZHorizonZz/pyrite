package dev.horizon.pyrite.impl.model;

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
        name: "acme.dev/Organization" plural: "organizations"
        definition {
          type: "organization"
          relations { key: "viewer" value { this {} } }
          metadata { relations { key: "viewer" value { directly_related_user_types { type: "user" } } } }
        }
        permissions { grant: "viewer" }
      }
      """;

  @Test
  @DisplayName("A type with parents gets the relation parent, and keeps its relations that read from it")
  void parent() throws Exception {
    TypeSystem types = new TypeSystemImpl(declaration(ORGANIZATION + """
        types {
          name: "acme.dev/Project" plural: "projects" parents: "acme.dev/Organization"
          definition {
            type: "project"
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
              relations { key: "owner" value { directly_related_user_types { type: "user" } } }
              relations { key: "viewer" value { directly_related_user_types { type: "user" } } }
            }
          }
          permissions { create: "viewer" grant: "viewer" }
        }
        """));
    WriteAuthorizationModelRequest model = types.model("store");
    TypeDefinition project = model.getTypeDefinitionsList().stream()
        .filter(definition -> definition.getType().equals("project")).findFirst().orElseThrow();
    TypeDefinition organization = model.getTypeDefinitionsList().stream()
        .filter(definition -> definition.getType().equals("organization")).findFirst().orElseThrow();

    assertThat(project.getRelationsMap().get("viewer").getUnion().getChildList()).extracting(Userset::getUsersetCase)
        .containsExactly(Userset.UsersetCase.THIS, Userset.UsersetCase.COMPUTED_USERSET,
            Userset.UsersetCase.TUPLE_TO_USERSET);
    assertThat(project.getRelationsMap().get("parent").getUsersetCase()).isEqualTo(Userset.UsersetCase.THIS);
    assertThat(project.getMetadata().getRelationsMap().get("parent").getDirectlyRelatedUserTypesList())
        .extracting(RelationReference::getType).containsExactly("organization");
    assertThat(organization.getRelationsMap()).doesNotContainKey("parent");
    assertThat(model.getTypeDefinitionsList()).extracting(TypeDefinition::getType)
        .containsExactly("user", "organization", "project");
  }

  @Test
  @DisplayName("A declaration names only what it declares")
  void references() {
    assertThatThrownBy(() -> new TypeSystemImpl(declaration(ORGANIZATION.replace("{ type: \"user\" }",
        "{ type: \"team\" relation: \"member\" }")))).hasMessageContaining("team");
    assertThatThrownBy(() -> new TypeSystemImpl(declaration(ORGANIZATION.replace("value { this {} }",
        "value { union { child { this {} } child { computed_userset { relation: \"admin\" } } } }"))))
        .hasMessageContaining("admin");
    assertThatThrownBy(() -> new TypeSystemImpl(declaration(ORGANIZATION.replace("value { this {} }",
        "value { union { child { this {} } child { tuple_to_userset { tupleset { relation: \"parent\" }"
            + " computed_userset { relation: \"viewer\" } } } } }"))))
        .hasMessageContaining("no parents");
    assertThatThrownBy(() -> new TypeSystemImpl(declaration(ORGANIZATION.replace("{ type: \"user\" }",
        "{ type: \"user\" condition: \"unknown\" }")))).hasMessageContaining("unknown");
    assertThatThrownBy(() -> new TypeSystemImpl(declaration(ORGANIZATION.replace("grant: \"viewer\"",
        "grant: \"editor\"")))).hasMessageContaining("editor");
  }

  @Test
  @DisplayName("A type does not declare the relation parent, which is Pyrite's")
  void reserved() {
    assertThatThrownBy(() -> new TypeSystemImpl(declaration(ORGANIZATION.replace("key: \"viewer\" value { this {} }",
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
