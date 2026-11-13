package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.schema.v1.Declaration;
import dev.horizon.pyrite.schema.v1.Type;

/**
 * A change of a type of the Acme declaration that keeps every rule, but changes what finds the resources of the type.
 */
public enum TypeChange {
  /**
   * No type acme.dev/Project.
   */
  PROJECT_DROP,
  /**
   * Projects have the plural ventures.
   */
  PROJECT_PLURAL,
  /**
   * Projects have the singular name venture.
   */
  PROJECT_SINGULAR,
  /**
   * Organizations are under organizations, created with admin.
   */
  ORGANIZATION_PARENTS,
  /**
   * Roles have the plural duties.
   */
  ROLE_PLURAL;

  Declaration apply(Declaration acme) {
    Declaration.Builder declaration = acme.toBuilder();
    for (int index = 0; index < declaration.getTypesCount(); index++) {
      Type.Builder type = declaration.getTypesBuilder(index);
      switch (this) {
        case PROJECT_DROP -> {
          if (type.getName().equals("acme.dev/Project")) {
            return declaration.removeTypes(index).build();
          }
        }
        case PROJECT_PLURAL -> {
          if (type.getName().equals("acme.dev/Project")) {
            type.setPlural("ventures");
          }
        }
        case PROJECT_SINGULAR -> {
          if (type.getName().equals("acme.dev/Project")) {
            type.getDefinitionBuilder().setType("venture");
          }
        }
        case ORGANIZATION_PARENTS -> {
          if (type.getName().equals("acme.dev/Organization")) {
            type.addParents("acme.dev/Organization").getPermissionsBuilder().setCreate("admin");
          }
        }
        case ROLE_PLURAL -> {
          if (type.getName().equals("acme.dev/Role")) {
            type.setPlural("duties");
          }
        }
      }
    }
    return declaration.build();
  }
}
