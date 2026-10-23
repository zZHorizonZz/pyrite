package dev.horizon.pyrite.impl.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.TextFormat;
import dev.horizon.pyrite.schema.v1.Declaration;
import dev.horizon.spectrum.openfga.v1.TypeDefinition;
import dev.horizon.spectrum.openfga.v1.Userset;
import dev.horizon.spectrum.openfga.v1.WriteAuthorizationModelRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TypeSystemTest {

  private static final String STANDARD = """
      fields { name: "name" kind: STRING }
      fields { name: "uid" kind: STRING }
      fields { name: "parent" kind: STRING }
      fields { name: "display_name" kind: STRING }
      fields { name: "labels" kind: MAP }
      fields { name: "revision" kind: INTEGER }
      fields { name: "create_time" kind: TIMESTAMP }
      fields { name: "update_time" kind: TIMESTAMP }
      fields { name: "delete_time" kind: TIMESTAMP }
      fields { name: "purge_time" kind: TIMESTAMP }
      """;
  private static final String ORGANIZATION = """
      types {
        name: "acme.dev/Organization" plural: "organizations" table: "acme_organization" %s
        definition {
          type: "organization"
          relations { key: "viewer" value { this {} } }
          metadata { relations { key: "viewer" value { directly_related_user_types { type: "user" } } } }
        }
        permissions { read: "viewer" update: "viewer" delete: "viewer" grant: "viewer" }
      }
      """.formatted(STANDARD);

  @Test
  @DisplayName("A relation that reads from the parent reaches OpenFGA as its direct types")
  void split() throws Exception {
    TypeSystem types = new TypeSystemImpl(declaration(ORGANIZATION + """
        types {
          name: "acme.dev/Project" plural: "projects" parents: "acme.dev/Organization" table: "acme_project" %s
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
            relations { key: "summary" value { computed_userset { relation: "owner" } } }
            metadata {
              relations { key: "owner" value { directly_related_user_types { type: "user" } } }
              relations { key: "viewer" value { directly_related_user_types { type: "user" } } }
            }
          }
          permissions { create: "viewer" read: "viewer" update: "viewer" delete: "viewer" grant: "viewer" }
        }
        """.formatted(STANDARD)));
    WriteAuthorizationModelRequest model = types.model("store");
    TypeDefinition project = model.getTypeDefinitionsList().stream()
        .filter(definition -> definition.getType().equals("project")).findFirst().orElseThrow();

    assertThat(project.getRelationsMap().get("viewer").getUsersetCase()).isEqualTo(Userset.UsersetCase.THIS);
    assertThat(project.getRelationsMap().get("summary").getComputedUserset().getRelation()).isEqualTo("owner");
    assertThat(project.getRelationsMap()).doesNotContainKey("parent");
    assertThat(model.getTypeDefinitionsList()).extracting(TypeDefinition::getType)
        .containsExactly("user", "organization", "project");
  }

  @Test
  @DisplayName("A type has every standard field")
  void standardFields() {
    assertThatThrownBy(() -> new TypeSystemImpl(declaration(ORGANIZATION.replace(
        "fields { name: \"revision\" kind: INTEGER }", ""))))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("revision");
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
    assertThatThrownBy(() -> new TypeSystemImpl(declaration(ORGANIZATION.replace("update: \"viewer\"",
        "update: \"editor\"")))).hasMessageContaining("editor");
  }

  @Test
  @DisplayName("A set that is granted cannot read from the parent")
  void grantedSet() {
    assertThatThrownBy(() -> new TypeSystemImpl(declaration(ORGANIZATION + """
        types {
          name: "acme.dev/Team" plural: "teams" parents: "acme.dev/Organization" table: "acme_team" %s
          definition {
            type: "team"
            relations {
              key: "viewer"
              value {
                union {
                  child { this {} }
                  child { tuple_to_userset { tupleset { relation: "parent" } computed_userset { relation: "viewer" } } }
                }
              }
            }
            metadata { relations { key: "viewer" value { directly_related_user_types { type: "user" } } } }
          }
          permissions { create: "viewer" read: "viewer" update: "viewer" delete: "viewer" grant: "viewer" }
        }
        types {
          name: "acme.dev/Document" plural: "documents" table: "acme_document" %s
          definition {
            type: "document"
            relations { key: "viewer" value { this {} } }
            metadata {
              relations {
                key: "viewer"
                value {
                  directly_related_user_types { type: "user" }
                  directly_related_user_types { type: "team" relation: "viewer" }
                }
              }
            }
          }
          permissions { read: "viewer" update: "viewer" delete: "viewer" grant: "viewer" }
        }
        """.formatted(STANDARD, STANDARD)))).hasMessageContaining("team#viewer");
  }

  private static Declaration declaration(String text) throws TextFormat.ParseException {
    Declaration.Builder declaration = Declaration.newBuilder();
    TextFormat.merge(text, declaration);
    return declaration.build();
  }
}
