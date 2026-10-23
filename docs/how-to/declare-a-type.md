# Declare a type

Declare a type to store a new kind of resource, such as a project with a billing account. The type names its table
and fields, its relations, and the relation that each method checks.

## Before you begin

- Write the declaration that you have now, or start from the one in [Get started](../get-started.md).
- Know the parent types of the new type, if it has any.

## Declare the type

1. Create the table, with the standard columns and one column per field of your own. For more information, refer to
   [Create the tables](create-the-tables.md).

2. Add a `types` entry with the name, the plural, the parents and the table:

   ```textproto
   types {
     name: "acme.dev/Project"
     plural: "projects"
     parents: "acme.dev/Organization"
     table: "acme_project"
   ```

3. List every column as a field: the ten standard fields, and your own:

   ```textproto
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
     fields { name: "billing_account" kind: STRING }
   ```

4. Write the OpenFGA type definition. Its `type` is the singular name. A relation with `this` lists the types that
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

5. Name the relation that each method checks, the relations that can be granted, the relation of the creator, and
   the retention of deleted resources:

   ```textproto
     permissions { create: "admin" read: "viewer" update: "editor" delete: "owner" grant: "owner" }
     grantable: ["owner", "editor", "viewer"]
     creator: "owner"
     retention { seconds: 2592000 }
   }
   ```

   `create` is a relation of the parent type. The others are relations of this type.

6. Write the declaration:

   ```java
   schema.writeDeclaration(WriteDeclarationRequest.newBuilder().setDeclaration(declaration).build()).await();
   ```

## Check the type

Create a resource of the type, and read its field back:

```java
Resource ledger = data.createResource(CreateResourceRequest.newBuilder().setSubject("user:anne")
    .setParent("organizations/acme").setResourceId("ledger").setResource(Resource.newBuilder()
        .setType("acme.dev/Project").putFields("billing_account", Value.newBuilder().setStringValue("b-1").build()))
    .build()).await();
System.out.println(ledger.getFieldsMap().get("billing_account").getStringValue());
```

```txt
b-1
```

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
