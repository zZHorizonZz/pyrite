package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.schema.v1.Declaration;
import dev.horizon.pyrite.schema.v1.Type;

/**
 * A change of the Acme declaration, for a declaration to write.
 */
public enum DeclarationChange {
  /**
   * Projects grant their creators editor, not owner.
   */
  EDITOR_CREATES,
  /**
   * A type with no definition, which breaks a rule.
   */
  EMPTY_TYPE,
  /**
   * Only the flows, which need the types of accounts.
   */
  FLOWS_ONLY;

  Declaration apply(Declaration acme) {
    return switch (this) {
      case EDITOR_CREATES -> {
        Declaration.Builder declaration = acme.toBuilder();
        for (Type.Builder type : declaration.getTypesBuilderList()) {
          if (type.getName().equals("acme.dev/Project")) {
            type.setCreator("editor");
          }
        }
        yield declaration.build();
      }
      case EMPTY_TYPE -> acme.toBuilder().addTypes(Type.newBuilder().setName("acme.dev/Folder")).build();
      case FLOWS_ONLY -> Declaration.newBuilder().addAllFlows(acme.getFlowsList()).build();
    };
  }
}
