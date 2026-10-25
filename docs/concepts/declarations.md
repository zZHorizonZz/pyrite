# Declarations

A declaration tells Pyrite about your application: its types of resources, the conditions that grants can carry, the
kinds of identifiers that accounts have, and the flows that accounts go through. It is a
`pyrite.schema.v1.Declaration` protobuf message. You write it with the schema service, over the same bus as every
other call.

## Types

A type is one kind of resource, such as an organization or a project. Your application keeps the resources
themselves. The type tells Pyrite two things about them:

- **Where they sit:** the type's name, the first segment of its resources' names, and its parents.
- **Who may do what:** an OpenFGA type definition with its relations, and the relations that Pyrite checks.

| Part         | Field         | Example                     |
| ------------ | ------------- | --------------------------- |
| Name         | `name`        | `acme.dev/Project`          |
| Name segment | `plural`      | `projects`                  |
| Parents      | `parents`     | `acme.dev/Organization`     |
| Relations    | `definition`  | `owner`, `editor`, `viewer` |
| Checks       | `permissions` | `create: "admin"`           |
| Grants       | `grantable`   | `owner`, `editor`, `viewer` |
| Creator      | `creator`     | `owner`                     |

The `definition` is OpenFGA's own `openfga.v1.TypeDefinition`, from Spectrum's protos. Its `type` is the type's
singular name, such as `project`. OpenFGA objects are named by it, such as `project:ledger`.

## The hierarchy

A type's `parents` are the types that its resources sit under. When your application makes a project known under
`organizations/acme`, Pyrite writes the tuple `organization:acme parent project:ledger` to OpenFGA. A type without
parents is at the top.

Pyrite adds the relation `parent` to every type that has parents, so do not declare it. A relation reads from the
parent with a tuple to userset whose tupleset is `parent`. For example, the viewers of a project include the members
of its organization:

```textproto
child { tuple_to_userset { tupleset { relation: "parent" } computed_userset { relation: "member" } } }
```

Because the parent is a real tuple, OpenFGA evaluates such a relation itself, in checks and in lists alike. For more
information, refer to [Resources](resources.md).

## Permissions

A type's `permissions` name the two relations that Pyrite checks on the subject of a call:

| Permission | Checked on   | By                                                                                         |
| ---------- | ------------ | ------------------------------------------------------------------------------------------ |
| `create`   | The parent   | `CreateObject`, and `CreateKey` for `pyrite.dev/Key`                                       |
| `grant`    | The resource | `CreateGrant`, `DeleteGrant`, `ListGrants`, `ListSubjects`, and checks of other principals |

A type without parents has no `create` permission. Any subject makes its resources known.

Every other relation is yours to check. Your application asks `Check` whether a subject has a relation, such as
`can_edit`, before it reads or changes a resource in its own tables. For more information, refer to
[Subjects and permissions](subjects-and-permissions.md).

## Pyrite's own types

`pyrite.dev/Key` and `pyrite.dev/Account` are types of Pyrite's own, whose resources Pyrite keeps. You declare them
like your types, with their relations, permissions and parents, but without `plural`: Pyrite names their resources
`keys/<id>` and `accounts/<id>`. The cryptography and authentication services check fixed relations on them, which
the declaration must have. For those relations, refer to [Pyrite's types](../reference/pyrite-types.md).

## Identifier kinds

An identifier kind is one kind of thing that a person signs in with, such as an email address or a username. Pyrite
has no kinds of its own. You declare each one with a name and two CEL expressions over the string `value`:

```textproto
identifier_kinds {
  name: "email"
  match: "value.trim().matches(r'^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$')"
  normalize: "value.trim().lowerAscii()"
  receives_codes: true
}
```

`match` says whether a value is of the kind. `normalize` gives the value that Pyrite stores and compares, so that
`Anne@Acme.dev` and `anne@acme.dev` are one identifier. `receives_codes` says whether your application can send a
code to it: an email address can, a username cannot. The order of the kinds matters, because Pyrite tries them in
that order. For more information, refer to [Accounts and flows](accounts-and-flows.md#identifier-kinds).

## The latest declaration

`WriteDeclaration` checks the declaration, writes its authorization model to OpenFGA, and stores it with an id. The
first write creates an OpenFGA store named `pyrite`, and later writes add models to it. Every call of every service
reads the latest declaration, so a new declaration takes effect at the next call.

For how to add a type, refer to [Declare a type](../how-to/declare-a-type.md).

## What a declaration is not

- A declaration is not a language. It is a protobuf message, which you can write in Java, in JSON or in text format.
- A declaration does not send messages. An identifier kind says whether codes go to it, and your application sends
  them.
- A declaration does not describe your tables. Pyrite never reads them, and your migrations create them.
- A declaration does not hold grants. Grants are OpenFGA tuples, which the access service writes.
- A declaration does not name roles that users define at runtime. Those are resources of a type that you declare. For
  more information, refer to [Let users define roles](../how-to/let-users-define-roles.md).
