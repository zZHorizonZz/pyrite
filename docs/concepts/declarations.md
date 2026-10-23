# Declarations

A declaration tells Pyrite what your application stores: its types of resources, the conditions that grants can
carry, and the flows that accounts go through. It is a `pyrite.schema.v1.Declaration` protobuf message. You write it
with the schema service, over the same bus as every other call.

## Types

A type is one kind of resource, such as an organization or a project. It has two parts:

- **The data:** its name, the first segment of its resources' names, its parents, its table and its fields.
- **The access:** an OpenFGA type definition with its relations, and the relations that each method checks.

| Part         | Field         | Example                     |
| ------------ | ------------- | --------------------------- |
| Name         | `name`        | `acme.dev/Project`          |
| Name segment | `plural`      | `projects`                  |
| Parents      | `parents`     | `acme.dev/Organization`     |
| Table        | `table`       | `acme_project`              |
| Columns      | `fields`      | `billing_account`, `STRING` |
| Relations    | `definition`  | `owner`, `editor`, `viewer` |
| Checks       | `permissions` | `read: "viewer"`            |
| Grants       | `grantable`   | `owner`, `editor`, `viewer` |
| Creator      | `creator`     | `owner`                     |
| Retention    | `retention`   | 30 days                     |

The `definition` is OpenFGA's own `openfga.v1.TypeDefinition`, from Spectrum's protos. Its `type` is the type's
singular name, such as `project`. OpenFGA objects are named by it, such as `project:ledger`.

## The hierarchy

A type's `parents` are the types that its resources live under. A project under `organizations/acme` has the parent
field `organizations/acme`. A type without parents is at the top.

A relation reads from the parent with a tuple to userset whose tupleset is `parent`. For example, the viewers of a
project include the members of its organization:

```textproto
child { tuple_to_userset { tupleset { relation: "parent" } computed_userset { relation: "member" } } }
```

Pyrite writes no `parent` tuples to OpenFGA. It walks the parent field of each resource instead. A move therefore
changes what a resource inherits at once, with no tuples to rewrite. For how Pyrite walks it, refer to
[Subjects and permissions](subjects-and-permissions.md).

## Permissions

Each method of the data and access services checks one relation, which the type's `permissions` names:

| Permission | Checked on   | By                                                              |
| ---------- | ------------ | --------------------------------------------------------------- |
| `create`   | The parent   | `CreateResource`, and `MoveResource` on the new parent          |
| `read`     | The resource | `GetResource`, and `QueryResources` when the request names none |
| `update`   | The resource | `UpdateResource` and `MoveResource`                             |
| `delete`   | The resource | `DeleteResource` and `UndeleteResource`                         |
| `grant`    | The resource | `CreateGrant`, `DeleteGrant`, `ListGrants` and `ListSubjects`   |

A type without parents has no `create` permission. Any subject creates its resources.

## Pyrite's own types

`pyrite.dev/Key` and `pyrite.dev/Account` are types of Pyrite's own. You declare them like your types, with their
relations and permissions, but without `plural`, `table` and `fields`: their tables are Pyrite's. Declaring them adds
the types beneath them, such as `pyrite.dev/KeyVersion` and `pyrite.dev/Session`. For their fields and the relations
that Pyrite checks, refer to [Pyrite's types](../reference/pyrite-types.md).

## The latest declaration

`WriteDeclaration` checks the declaration, writes its authorization model to OpenFGA, and stores it with an id. The
first write creates an OpenFGA store named `pyrite`, and later writes add models to it. Every call of every service
reads the latest declaration, so a new declaration takes effect at the next call.

Pyrite does not change your tables. Create the table and the columns of a type before you declare it. For more
information, refer to [Declare a type](../how-to/declare-a-type.md).

## What a declaration is not

- A declaration is not a language. It is a protobuf message, which you can write in Java, in JSON or in text format.
- A declaration does not create tables. Your migrations do.
- A declaration does not hold grants. Grants are OpenFGA tuples, which the access service writes.
- A declaration does not name roles that users define at runtime. Those are resources of a type that you declare. For
  more information, refer to [Let users define roles](../how-to/let-users-define-roles.md).
