# Declaration

A `pyrite.schema.v1.Declaration` declares the types, conditions, identifier kinds and flows of your application.
`WriteDeclaration` checks it against the rules on this page, and fails with `INVALID_ARGUMENT` at the first rule that
it breaks. For how to write one, refer to [Declare a type](../how-to/declare-a-type.md).

## Declaration

| Field              | Type                                      | Description                                                   |
| ------------------ | ----------------------------------------- | ------------------------------------------------------------- |
| `types`            | repeated `Type`                           | The types of resources                                        |
| `conditions`       | map of `string` to `openfga.v1.Condition` | The conditions that relations name, by name                   |
| `identifier_kinds` | repeated `IdentifierKind`                 | The kinds of identifiers, in the order that Pyrite tries them |
| `flows`            | repeated `FlowDefinition`                 | The flows of the authentication service                       |

## Type

| Field         | Type                        | Default | Description                                                         |
| ------------- | --------------------------- | ------- | ------------------------------------------------------------------- |
| `name`        | `string`                    | none    | The type's name, such as `acme.dev/Project`                         |
| `plural`      | `string`                    | none    | The first segment of its resources' names, such as `projects`       |
| `parents`     | repeated `string`           | none    | The types that its resources sit under; none for a type at the top  |
| `definition`  | `openfga.v1.TypeDefinition` | none    | The OpenFGA type: its singular name, relations and direct types     |
| `permissions` | `Permissions`               | none    | The relations that Pyrite checks                                    |
| `grantable`   | repeated `string`           | none    | The relations that the access service grants and revokes            |
| `creator`     | `string`                    | `""`    | The relation granted to the creator of a resource; empty for none   |

`pyrite.dev/Key` and `pyrite.dev/Account` leave `plural` empty. For their requirements, refer to
[Pyrite's types](pyrite-types.md).

Pyrite adds the relation `parent` to the OpenFGA type of every type that has parents. It takes the singular names of
the parent types directly.

## Permissions

| Field    | Checked on   | Description                                                                                 |
| -------- | ------------ | ------------------------------------------------------------------------------------------- |
| `create` | The parent   | Makes a resource known under the parent, or creates a key. Empty for a type without parents |
| `grant`  | The resource | Grants, revokes and lists grants, and asks about other principals                           |

## FlowDefinition

| Field       | Type                                 | Description                                                           |
| ----------- | ------------------------------------ | --------------------------------------------------------------------- |
| `name`      | `string`                             | The flow's name, such as `sign-in`                                    |
| `input`     | `FlowDefinition.Input`               | `IDENTIFIER`, `SUBJECT` or `SUBJECT_IDENTIFIER`                       |
| `stages`    | repeated `Stage`                     | What has to be proven, in order                                       |
| `completes` | repeated `FlowDefinition.Completion` | `ISSUE_SESSION`, `MARK_VERIFIED`, `ACTIVATE_FACTOR` or `END_SESSIONS` |

## Stage

| Field                | Type           | Default | Description                                                 |
| -------------------- | -------------- | ------- | ----------------------------------------------------------- |
| `factors`            | `int32`        | `0`     | The distinct factors that the stage requires, `0` to `3`    |
| `phishing_resistant` | `bool`         | `false` | Whether the stage requires a passkey                        |
| `identifier`         | `bool`         | `false` | Whether the stage requires a code sent to the identifier    |
| `enrol`              | `Stage.Factor` | none    | `PASSWORD`, `TOTP`, `PASSKEY` or `RECOVERY_CODES`, to enrol |

## IdentifierKind

| Field            | Type     | Default | Description                                                                 |
| ---------------- | -------- | ------- | --------------------------------------------------------------------------- |
| `name`           | `string` | none    | The kind's name, such as `email` or `employee-id`                           |
| `match`          | `string` | none    | A CEL expression over `value` that is `true` when the value is of this kind |
| `normalize`      | `string` | `""`    | A CEL expression over `value` that gives the stored value; empty keeps it   |
| `receives_codes` | `bool`   | `false` | Whether your application sends codes to identifiers of this kind            |

Both expressions take one variable, `value`, the string as the caller gave it. They can use CEL's standard functions
and its strings extension, such as `trim()`, `lowerAscii()`, `replace()` and `matches()`. For example:

```textproto
identifier_kinds {
  name: "email"
  match: "value.trim().matches(r'^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$')"
  normalize: "value.trim().lowerAscii()"
  receives_codes: true
}
identifier_kinds {
  name: "phone"
  match: "value.trim().replace(' ', '').replace('-', '').matches(r'^\\+[1-9][0-9]{6,14}$')"
  normalize: "value.trim().replace(' ', '').replace('-', '')"
  receives_codes: true
}
identifier_kinds {
  name: "username"
  match: "value.trim().lowerAscii().matches(r'^[a-z0-9][a-z0-9._-]{2,63}$')"
  normalize: "value.trim().lowerAscii()"
}
```

The normalized value must meet `match` too, or Pyrite treats the value as of no kind. A value whose expression fails
to evaluate is of no kind. For how Pyrite picks a kind, refer to
[Accounts and flows](../concepts/accounts-and-flows.md#identifier-kinds).

## Rules of types

| Rule                                                                                |
| ----------------------------------------------------------------------------------- |
| A type has a `name`, and no two types have the same one.                            |
| A name that starts with `pyrite.dev/` is `pyrite.dev/Key` or `pyrite.dev/Account`.  |
| The singular name, `definition.type`, matches `[a-z][a-z0-9_]{0,49}` and is unique. |
| Only `pyrite.dev/Account` has the singular name `user`.                             |
| `plural` matches `[a-z][a-zA-Z0-9]{0,62}` and is unique.                            |
| `pyrite.dev/Key` and `pyrite.dev/Account` leave `plural` empty.                     |
| Each parent is a declared type.                                                     |

## Rules of relations

| Rule                                                                                                                           |
| ------------------------------------------------------------------------------------------------------------------------------ |
| A relation name matches `[a-z][a-z0-9_]{0,49}`, and is not `parent`.                                                           |
| A relation has direct types in `metadata` exactly when its rewrite has `this`.                                                 |
| `metadata` describes only relations of the type.                                                                               |
| A direct type is `user` without a relation, or a declared singular name, with a relation of that type if any.                  |
| A direct type's condition is a declared condition.                                                                             |
| A computed userset names a relation of the same type.                                                                          |
| A tuple to userset through `parent` is on a type with parents, and each parent type has the computed relation.                 |
| A tuple to userset through another relation goes through a relation with direct types, whose types have the computed relation. |
| Each key of `conditions` is the `name` of its condition.                                                                       |

## Rules of permissions

| Rule                                                                                                         |
| ------------------------------------------------------------------------------------------------------------ |
| `grant` is a relation of the type.                                                                           |
| A type without parents has an empty `create`.                                                                |
| A type with parents has a `create` that is a relation of every parent type.                                  |
| Each `grantable` relation is a relation of the type with direct types.                                       |
| A non-empty `creator` is a `grantable` relation that takes `user` directly, without a condition or wildcard. |
| `pyrite.dev/Key` has the relations `admin`, `viewer`, `encrypter`, `decrypter`, `signer` and `verifier`.     |
| `pyrite.dev/Account` has the relations `owner`, `admin` and `viewer`.                                        |
| The `owner` of `pyrite.dev/Account` takes `user` directly, without a condition or wildcard.                  |

## Rules of identifier kinds

| Rule                                                                                                |
| --------------------------------------------------------------------------------------------------- |
| A kind name matches `[a-z][a-z0-9-]{0,62}`, and no two kinds have the same one.                     |
| A kind has a non-empty `match`.                                                                     |
| `match` compiles as a CEL expression over the string `value` that gives a `bool`.                   |
| A non-empty `normalize` compiles as a CEL expression over the string `value` that gives a `string`. |

## Rules of flows

| Rule                                                                                                  |
| ----------------------------------------------------------------------------------------------------- |
| A declaration with flows declares `pyrite.dev/Account`.                                               |
| A flow name matches `[a-z][a-z0-9-]{0,62}`, and no two flows have the same one.                       |
| A flow has an input, at least one stage and at least one completion, and every completion has a kind. |
| A flow whose input is `IDENTIFIER` or `SUBJECT_IDENTIFIER` needs at least one identifier kind.        |
| A flow with a stage with `identifier` needs an identifier kind with `receives_codes`.                 |
| A stage requires 0 to 3 factors, and requires at least one thing.                                     |
| A stage that enrols requires nothing else.                                                            |
| A flow enrols at most once, after the account is proven by the input `SUBJECT` or an earlier stage.   |
| `ISSUE_SESSION` needs the input `IDENTIFIER` and a stage of factors or a passkey.                     |
| `MARK_VERIFIED` needs the input `SUBJECT_IDENTIFIER` and a stage with `identifier`.                   |
| `ACTIVATE_FACTOR` needs a stage that enrols.                                                          |
| `END_SESSIONS` needs a proven account.                                                                |

## Rules of the options

These rules fail with `FAILED_PRECONDITION`, because the options of the service break them, not the declaration:

| Rule                                                                                           |
| ---------------------------------------------------------------------------------------------- |
| A declaration with `pyrite.dev/Key` needs a root key in the options.                           |
| A declaration with `pyrite.dev/Account` needs `pyrite.dev/Key` and the authentication options. |
