# Declare a type

Declare a type so that Pyrite knows a new kind of resource, such as a project. The type names its parents, its
relations, and the relations that Pyrite checks. Your application keeps the resources themselves, in its own tables.

## Before you begin

- Write the declaration that you have now, or start from the one in [Get started](../get-started.md).
- Know the parent types of the new type, if it has any.

## Declare the type

1. Add a `types` entry with the name, the plural and the parents:

   ```textproto
   types {
     name: "acme.dev/Project"
     plural: "projects"
     parents: "acme.dev/Organization"
   ```

   The plural is the first segment of the resources' names, such as `projects/ledger`. Use the same names in your
   tables.

2. Write the OpenFGA type definition. Its `type` is the singular name. A relation with `this` lists the types that
   take it directly in `metadata`. Here the editors of a project include the admins of its organization:

   ```textproto
     definition {
       type: "project"
       relations { key: "owner" value { this {} } }
       relations {
         key: "editor"
         value {
           union {
             child { this {} }
             child { computed_userset { relation: "owner" } }
             child { tuple_to_userset { tupleset { relation: "parent" } computed_userset { relation: "admin" } } }
           }
         }
       }
       relations {
         key: "viewer"
         value { union { child { this {} } child { computed_userset { relation: "editor" } } } }
       }
       metadata {
         relations { key: "owner" value { directly_related_user_types { type: "user" } } }
         relations { key: "editor" value { directly_related_user_types { type: "user" } } }
         relations {
           key: "viewer"
           value {
             directly_related_user_types { type: "user" }
             directly_related_user_types { type: "user" wildcard {} }
           }
         }
       }
     }
   ```

   Do not declare the relation `parent`. Pyrite adds it to every type that has parents.

3. Name the relations that Pyrite checks, the relations that can be granted, and the relation of the creator:

   ```textproto
     permissions { create: "admin" grant: "owner" }
     grantable: ["owner", "editor", "viewer"]
     creator: "owner"
   }
   ```

   `create` is a relation of the parent type, which `CreateObject` checks on the parent. `grant` is a relation of
   this type, which the grant methods check on the resource.

4. Write the declaration:

   ```java
   schema.writeDeclaration(WriteDeclarationRequest.newBuilder().setDeclaration(declaration).build()).await();
   ```

## Check the type

Make a resource of the type known, and check a relation on it:

```java
access.createObject(CreateObjectRequest.newBuilder().setSubject("user:anne").setName("projects/ledger")
    .setParent("organizations/acme").build()).await();
boolean allowed = access.check(CheckRequest.newBuilder().setSubject("user:anne").setObject("projects/ledger")
    .setPermission("editor").build()).await().getAllowed();
System.out.println(allowed);
```

```txt
true
```

Anne is an editor twice over: she is the project's `owner`, the type's `creator` relation, and an `admin` of its
organization.

When the declaration breaks a rule, `WriteDeclaration` fails with `INVALID_ARGUMENT`, and the message names the type
and the rule. For every rule, refer to [Declaration](../reference/declaration.md).

## Add a condition to a grant

A relation can take grants that hold only while a condition is true, such as an expiry time.

1. Declare the condition, with its CEL expression and parameters:

   ```textproto
   conditions {
     key: "not_expired"
     value {
       name: "not_expired"
       expression: "current_time < expires_at"
       parameters { key: "current_time" value { type_name: TYPE_NAME_TIMESTAMP } }
       parameters { key: "expires_at" value { type_name: TYPE_NAME_TIMESTAMP } }
     }
   }
   ```

2. Let the relation take a user with the condition:

   ```textproto
   directly_related_user_types { type: "user" condition: "not_expired" }
   ```

3. Grant with the condition and the values that it fixes:

   ```java
   access.createGrant(CreateGrantRequest.newBuilder().setSubject("user:anne").setGrant(Grant.newBuilder()
       .setObject("projects/ledger").setRelation("editor").setGrantee("user:bob")
       .setCondition(Condition.newBuilder().setName("not_expired").setContext(Struct.newBuilder()
           .putFields("expires_at", com.google.protobuf.Value.newBuilder()
               .setStringValue("2026-06-01T00:00:00Z").build()))))
       .build()).await();
   ```

4. Give the other values in the `context` of each check, such as `current_time`.

## Related resources

- [Declarations](../concepts/declarations.md)
- [Declaration](../reference/declaration.md)
- [OpenFGA: Concepts](https://openfga.dev/docs/concepts), for the meaning of each rewrite
