# Declare a type

Declare a type so that Pyrite knows a new kind of resource, such as a project, with its parents, its relations and
the relations that Pyrite checks.

## Before you begin

- Serve Pyrite and write a first declaration, as [Get started](../get-started.md) does. The examples add the type
  `acme.dev/Project` under its `acme.dev/Organization`, whose relation `admin` its creator holds.
- Make `organizations/acme` known as `user:anne`, so that Anne is its `admin`.
- Keep the resources of the new type in a table of your application. Pyrite keeps only their names, types and
  parents.

## Declare the type

1. Add a `types` entry with the name, the plural and the parents:

   ```textproto
   types {
     name: "acme.dev/Project"
     plural: "projects"
     parents: "acme.dev/Organization"
   ```

   The plural is the first segment of the names of the resources, such as `projects/ledger`. Use the same names in
   your tables.

2. Write the OpenFGA type definition. Its `type` is the singular name. A relation with `this` takes grants, and lists
   the types that it takes in `metadata`. Here the editors of a project include its owners and the admins of its
   organization:

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

   Do not declare the relation `parent`. Pyrite adds it to every type that has parents, and a `tuple_to_userset`
   through `parent` reads a relation that each parent type has.

3. Name the relations that Pyrite checks, the relations that can be granted, and the relation of the creator:

   ```textproto
     permissions { create: "admin" grant: "owner" }
     grantable: ["owner", "editor", "viewer"]
     creator: "owner"
   }
   ```

   `create` is a relation of each parent type, which `CreateObject` checks on the parent. `grant` is a relation of
   this type, which the grant methods check on the resource. Each `grantable` relation takes grants, and the
   `creator` is one of them that takes `user` directly, without a condition or a wildcard.

4. Write the whole declaration, with the types that it had and the new one:

   ```java
   schema.writeDeclaration(WriteDeclarationRequest.newBuilder().setDeclaration(declaration).build()).await();
   ```

   A declaration replaces the one before it, so a type that you leave out is no longer declared.

## Verify

Make a resource of the type known, and check a relation on it:

```java
access.createObject(CreateObjectRequest.newBuilder().setSubject("user:anne").setName("projects/ledger")
    .setParent("organizations/acme").build()).await();
boolean allowed = access.check(CheckRequest.newBuilder().setSubject("user:anne").setObject("projects/ledger")
    .setPermission("editor").build()).await().getAllowed();
System.out.println(allowed);
```

```text
true
```

Anne is an editor twice: she is the `owner` of the project, the type's `creator` relation, and an `admin` of its
organization. A declaration that breaks a rule fails `WriteDeclaration` with `INVALID_ARGUMENT`, and the message names
the type and the rule. See [Troubleshooting declarations](troubleshooting-declarations.md).

## Add a condition to a grant

A relation can take grants that hold only while a condition is true, such as until an expiry time.

1. Declare the condition, with its CEL expression and its parameters. Its key is its name:

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

2. Let the relation take a user with the condition, next to its other direct types:

   ```textproto
   relations {
     key: "editor"
     value {
       directly_related_user_types { type: "user" }
       directly_related_user_types { type: "user" condition: "not_expired" }
     }
   }
   ```

3. Write the declaration, and grant with the condition and the values that the grant holds:

   ```java
   access.createGrant(CreateGrantRequest.newBuilder().setSubject("user:anne").setGrant(Grant.newBuilder()
       .setObject("projects/ledger").setRelation("editor").setGrantee("user:bob")
       .setCondition(Condition.newBuilder().setName("not_expired").setContext(Struct.newBuilder()
           .putFields("expires_at", Value.newBuilder().setStringValue("2026-06-01T00:00:00Z").build()))))
       .build()).await();
   ```

4. Give the other values in the `context` of each check, such as `current_time`:

   ```java
   boolean allowed = access.check(CheckRequest.newBuilder().setSubject("user:bob").setObject("projects/ledger")
       .setPermission("editor").setContext(Struct.newBuilder().putFields("current_time",
           Value.newBuilder().setStringValue("2026-05-01T00:00:00Z").build())).build()).await().getAllowed();
   ```

   `allowed` is `true` on 1 May 2026, and `false` from 1 June. A value that the grant holds wins over a value of the
   same name in the context.

## Related

- [About declarations](about-declarations.md)
- [Declaration reference](declaration-reference.md)
- [OpenFGA: Concepts](https://openfga.dev/docs/concepts), for the meaning of each rewrite
