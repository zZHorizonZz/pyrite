# About declarations

A declaration tells Pyrite which types of resources your application has, where they sit, and who may do what on
them. It also declares the conditions that grants can carry, the kinds of identifiers that accounts have, and the
flows of the authentication service. It is a `pyrite.schema.v1.Declaration` message, which you can write in Java, in
JSON or in protobuf text format.

```textproto
types {
  name: "acme.dev/Project"
  plural: "projects"
  parents: "acme.dev/Organization"
  definition {
    type: "project"
    relations { key: "owner" value { this {} } }
    relations {
      key: "viewer"
      value {
        union {
          child { this {} }
          child { computed_userset { relation: "owner" } }
          child { tuple_to_userset { tupleset { relation: "parent" } computed_userset { relation: "member" } } }
        }
      }
    }
    metadata {
      relations { key: "owner" value { directly_related_user_types { type: "user" } } }
      relations { key: "viewer" value { directly_related_user_types { type: "user" } } }
    }
  }
  permissions { create: "admin" grant: "owner" }
  grantable: ["owner", "viewer"]
  creator: "owner"
}
```

With this type, a resource `projects/ledger` sits under an organization, such as `organizations/acme`. Making it known
needs `admin` on the organization. Its creator becomes its `owner`, an owner grants `owner` and `viewer` on it, and the
members of the organization view it.

## Types

A type is one kind of resource, such as an organization or a project. Your application keeps the resources
themselves, in its own tables. The type tells Pyrite where they sit and who may do what on them:

| Part         | Field         | Example                                |
| ------------ | ------------- | -------------------------------------- |
| Name         | `name`        | `acme.dev/Project`                     |
| Name segment | `plural`      | `projects`, as in `projects/ledger`    |
| Parents      | `parents`     | `acme.dev/Organization`                |
| Relations    | `definition`  | `owner`, `viewer`                      |
| Checks       | `permissions` | `create: "admin"`, `grant: "owner"`    |
| Grants       | `grantable`   | `owner`, `viewer`                      |
| Creator      | `creator`     | `owner`                                |

The `definition` is an OpenFGA type definition, `openfga.v1.TypeDefinition` from Spectrum's protos. Its `type` is the
singular name of the type, such as `project`, and OpenFGA names the resources of the type by it, such as
`project:ledger`. Each relation of the definition is a rewrite: `this` takes grants, `computed_userset` reads another
relation of the same resource, and `tuple_to_userset` reads a relation of the resources that another relation names.
`union`, `intersection` and `difference` combine them. The `metadata` lists, for each relation with `this`, the types
that it takes.

## The hierarchy

A type's `parents` are the types that its resources sit under. A type without parents is at the top. Pyrite adds the
relation `parent` to each type that has parents, and you do not declare it. When your application makes
`projects/ledger` known under `organizations/acme`, Pyrite writes the tuple `organization:acme parent project:ledger`
to OpenFGA.

A relation reads from the parent through a `tuple_to_userset` whose tupleset is `parent`:

```textproto
child { tuple_to_userset { tupleset { relation: "parent" } computed_userset { relation: "member" } } }
```

Because the parent is a tuple in OpenFGA, OpenFGA follows it in checks and in lists alike. A grant of `member` on the
organization reaches every project under it. See [About resources](../access/about-resources.md).

## Permissions

A type's `permissions` name the two relations that Pyrite checks on the subject of a call:

| Permission | Checked on   | By                                                                                     |
| ---------- | ------------ | -------------------------------------------------------------------------------------- |
| `create`   | The parent   | `CreateObject`, and `CreateKey` for `pyrite.dev/Key`                                   |
| `grant`    | The resource | `DeleteObject`, `CreateGrant`, `DeleteGrant`, `ListGrants`, `ListSubjects`, `ListChanges`, and a `Check` of another principal |

A type without parents has no `create` permission: any subject makes its resources known. Every other relation is
yours to check. Your application asks `Check` whether a subject has a relation, such as `can_edit`, before it reads
or changes a resource in its own tables. See [About subjects and permissions](../access/about-subjects-and-permissions.md).

`grantable` lists the relations that the access service grants and revokes. `creator` is the relation that Pyrite
grants the subject that makes a resource known, so that someone can grant on the new resource.

## Pyrite's own types

`pyrite.dev/Key` and `pyrite.dev/Account` are types whose resources Pyrite keeps: keys with their versions, and
accounts with their identifiers, factors and sessions. You declare them as your own types, with their relations,
permissions and parents, but without `plural`: Pyrite names their resources `keys/<id>` and `accounts/<id>`. The
cryptography and authentication services check fixed relations on them, which the declaration must have.

The singular name `user` belongs to `pyrite.dev/Account`, so `user:anne` is the account `accounts/anne`. A
declaration without `pyrite.dev/Account` still gives OpenFGA a type `user`, without relations, for the subjects of
calls. See [Pyrite's types](pyrite-types.md).

## Identifier kinds and flows

An identifier kind is one kind of thing that a person signs in with, such as an email address or a username. Pyrite
has no kinds of its own. You declare each with a name and two CEL expressions over the string `value`:

```textproto
identifier_kinds {
  name: "email"
  match: "value.trim().matches(r'^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$')"
  normalize: "value.trim().lowerAscii()"
  receives_codes: true
}
```

`match` tells whether a value is of the kind. `normalize` gives the value that Pyrite stores and compares, so that
`Anne@Acme.dev` and `anne@acme.dev` are one identifier. `receives_codes` tells whether your application can send a
code to it. A value without a named kind takes the first kind, in the order of the declaration, whose `match` it
meets.

A flow definition names what a flow of the authentication service starts from, the stages that it has to prove, and
what it does when it ends, such as issuing a session. A declaration with flows declares `pyrite.dev/Account`. See
[About accounts and flows](../accounts/about-accounts-and-flows.md).

## The latest declaration

`WriteDeclaration` checks the whole declaration, writes its authorization model to OpenFGA, and stores it in the
table `pyrite_declaration` with a new id. The first write creates an OpenFGA store named `pyrite`, and each later
write adds a model to that store, so the grants stay from one declaration to the next. A declaration that breaks a
rule fails with `INVALID_ARGUMENT` and changes nothing: the latest declaration stays.

Every call of every service reads the id of the latest declaration from the database, so a new declaration takes
effect at the next call, in every Pyrite on the same database. Before the first declaration, every call fails with
`FAILED_PRECONDITION`, and `ReadDeclaration` fails with `NOT_FOUND`. `ReadDeclaration` gives back a declaration as it
was written: the latest one for an empty `id`, or the one of an `id`, with the time that the database stored it.

The schema service takes no subject, so any caller that reaches it can write a declaration. Serve it only to your
application. See [Serve the services](../deployment/serve-the-services.md).

## What a declaration does not hold

- Your resources. Pyrite never reads your tables, and your migrations create them.
- Grants. Grants are OpenFGA tuples, which the access service writes.
- Roles that users define at runtime. Those are resources of types that you declare. See
  [Let users define roles](let-users-define-roles.md).

## Related

- [Declare a type](declare-a-type.md)
- [Declaration reference](declaration-reference.md)
- [Troubleshooting declarations](troubleshooting-declarations.md)
