# Let users define roles

Let the administrators of your application define their own roles at runtime, such as "Stock admin", and bind them to
people on an organization or a project. The declaration fixes which permissions a role can carry. Users decide which
roles exist and who holds them.

Roles and bindings are not built into Pyrite. They are two types that you declare, as in OpenFGA's custom roles
pattern.

## Before you begin

- Declare the type of the resources that roles apply to, such as `acme.dev/Organization` and `acme.dev/Project`.
- Keep roles in a table of your application, with what people see of them, such as the name "Stock admin".

## Declare roles and bindings

1. Declare the role type, under the organization. Give it one relation per permission that a role can carry, such as
   `can_manage_stock`, that takes every user:

   ```textproto
   definition {
     type: "role"
     relations {
       key: "admin"
       value { tuple_to_userset { tupleset { relation: "parent" } computed_userset { relation: "admin" } } }
     }
     relations { key: "can_manage_stock" value { this {} } }
     metadata {
       relations { key: "can_manage_stock" value { directly_related_user_types { type: "user" wildcard {} } } }
     }
   }
   permissions { create: "admin" grant: "admin" }
   grantable: ["can_manage_stock"]
   ```

2. Declare the binding type, under the organization. A binding has a role and members. A permission holds for the
   members when the role carries it:

   ```textproto
   definition {
     type: "binding"
     relations {
       key: "admin"
       value { tuple_to_userset { tupleset { relation: "parent" } computed_userset { relation: "admin" } } }
     }
     relations { key: "role" value { this {} } }
     relations { key: "member" value { this {} } }
     relations {
       key: "can_manage_stock"
       value {
         intersection {
           child { computed_userset { relation: "member" } }
           child { tuple_to_userset { tupleset { relation: "role" } computed_userset { relation: "can_manage_stock" } } }
         }
       }
     }
     metadata {
       relations { key: "role" value { directly_related_user_types { type: "role" } } }
       relations { key: "member" value { directly_related_user_types { type: "user" } } }
     }
   }
   permissions { create: "admin" grant: "admin" }
   grantable: ["role", "member"]
   ```

3. On each type that roles apply to, add a relation that takes bindings, and read the permission through it. On a
   project, also read it from the parent, so that a binding on the organization reaches its projects:

   ```textproto
   relations { key: "binding" value { this {} } }
   relations {
     key: "can_manage_stock"
     value {
       union {
         child { tuple_to_userset { tupleset { relation: "binding" } computed_userset { relation: "can_manage_stock" } } }
         child { tuple_to_userset { tupleset { relation: "parent" } computed_userset { relation: "can_manage_stock" } } }
       }
     }
   }
   ```

   Add `binding` to the type's `grantable` relations, and to its `metadata` with the direct type `binding`.

4. Write the declaration.

## Define a role and bind it

An administrator of the organization does these steps at runtime, through your application and the access service.

1. Create the role in your table, with the name "Stock admin", and make it known:

   ```java
   access.createObject(CreateObjectRequest.newBuilder().setSubject("user:anne").setName("roles/stock-admin")
       .setParent("organizations/acme").build()).await();
   ```

2. Give the role its permissions, by granting each one to every user on the role:

   ```java
   access.createGrant(CreateGrantRequest.newBuilder().setSubject("user:anne").setGrant(Grant.newBuilder()
       .setObject("roles/stock-admin").setRelation("can_manage_stock").setGrantee("user:*")).build()).await();
   ```

3. Make a binding known, and give it the role and a member:

   ```java
   access.createObject(CreateObjectRequest.newBuilder().setSubject("user:anne").setName("bindings/stock")
       .setParent("organizations/acme").build()).await();
   access.createGrant(CreateGrantRequest.newBuilder().setSubject("user:anne").setGrant(Grant.newBuilder()
       .setObject("bindings/stock").setRelation("role").setGrantee("role:stock-admin")).build()).await();
   access.createGrant(CreateGrantRequest.newBuilder().setSubject("user:anne").setGrant(Grant.newBuilder()
       .setObject("bindings/stock").setRelation("member").setGrantee("user:bob")).build()).await();
   ```

   A grantee names a resource by its OpenFGA object: the type's singular name and the id, such as
   `role:stock-admin`.

4. Attach the binding where it applies, here the whole organization:

   ```java
   access.createGrant(CreateGrantRequest.newBuilder().setSubject("user:anne").setGrant(Grant.newBuilder()
       .setObject("organizations/acme").setRelation("binding").setGrantee("binding:stock")).build()).await();
   ```

## Check the binding

Check the permission on a project of the organization:

```java
boolean allowed = access.check(CheckRequest.newBuilder().setSubject("user:anne").setPrincipal("user:bob")
    .setObject("projects/ledger").setPermission("can_manage_stock").build()).await().getAllowed();
```

`allowed` is `true`. `ListObjects` of `acme.dev/Project` with the permission `can_manage_stock` returns the
organization's projects for Bob. A binding attached to `projects/ledger` instead applies to that project only.

## Related resources

- [Subjects and permissions](../concepts/subjects-and-permissions.md)
- [OpenFGA: Custom roles](https://openfga.dev/docs/modeling/custom-roles)
