# Declaration

A `pyrite.schema.v1.Declaration` declares the types, conditions and flows of your application. `WriteDeclaration`
checks it against the rules on this page, and fails with `INVALID_ARGUMENT` at the first rule that it breaks. For how
to write one, refer to [Declare a type](../how-to/declare-a-type.md).

## Declaration

| Field        | Type                                      | Description                                 |
| ------------ | ----------------------------------------- | ------------------------------------------- |
| `types`      | repeated `Type`                           | The types of resources                      |
| `conditions` | map of `string` to `openfga.v1.Condition` | The conditions that relations name, by name |
| `flows`      | repeated `FlowDefinition`                 | The flows of the authentication service     |

## Type

| Field         | Type                        | Default | Description                                                         |
| ------------- | --------------------------- | ------- | ------------------------------------------------------------------- |
| `name`        | `string`                    | none    | The type's name, such as `acme.dev/Project`                         |
| `plural`      | `string`                    | none    | The first segment of its resources' names, such as `projects`       |
| `parents`     | repeated `string`           | none    | The types that its resources live under; none for a type at the top |
| `table`       | `string`                    | none    | The table of its resources                                          |
| `fields`      | repeated `Field`            | none    | The columns of the table: the standard fields and the type's own    |
| `definition`  | `openfga.v1.TypeDefinition` | none    | The OpenFGA type: its singular name, relations and direct types     |
| `permissions` | `Permissions`               | none    | The relation that each method checks                                |
| `grantable`   | repeated `string`           | none    | The relations that the access service grants and revokes            |
| `creator`     | `string`                    | `""`    | The relation granted to the creator of a resource; empty for none   |
| `retention`   | `google.protobuf.Duration`  | `0s`    | How long a deleted resource stays before it can be purged           |

`pyrite.dev/Key` and `pyrite.dev/Account` leave `plural`, `table` and `fields` empty. For their requirements, refer
to [Pyrite's types](pyrite-types.md).

## Field

| Field  | Type         | Description                                                                      |
| ------ | ------------ | -------------------------------------------------------------------------------- |
| `name` | `string`     | The column's name                                                                |
| `kind` | `Field.Kind` | `STRING`, `INTEGER`, `DOUBLE`, `BOOLEAN`, `BYTES`, `TIMESTAMP`, `MAP` or `ARRAY` |

Every type has the ten standard fields, with these kinds:

| Field          | Kind        |
| -------------- | ----------- |
| `name`         | `STRING`    |
| `uid`          | `STRING`    |
| `parent`       | `STRING`    |
| `display_name` | `STRING`    |
| `labels`       | `MAP`       |
| `revision`     | `INTEGER`   |
| `create_time`  | `TIMESTAMP` |
| `update_time`  | `TIMESTAMP` |
| `delete_time`  | `TIMESTAMP` |
| `purge_time`   | `TIMESTAMP` |

## Permissions

| Field    | Checked on   | Description                                                           |
| -------- | ------------ | --------------------------------------------------------------------- |
| `create` | The parent   | Creates a resource, or moves one in. Empty for a type without parents |
| `read`   | The resource | Gets a resource, and the default permission of a query                |
| `update` | The resource | Updates or moves a resource                                           |
| `delete` | The resource | Deletes or undeletes a resource                                       |
| `grant`  | The resource | Grants, revokes and lists grants, and asks about other principals     |

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

## Rules of types

| Rule                                                                                |
| ----------------------------------------------------------------------------------- |
| A type has a `name`, and no two types have the same one.                            |
| A name that starts with `pyrite.dev/` is `pyrite.dev/Key` or `pyrite.dev/Account`.  |
| The singular name, `definition.type`, matches `[a-z][a-z0-9_]{0,49}` and is unique. |
| Only `pyrite.dev/Account` has the singular name `user`.                             |
| `plural` matches `[a-z][a-zA-Z0-9]{0,62}` and is unique.                            |
| `table` is not empty.                                                               |
| `retention` is not negative.                                                        |
| Each field name matches `[a-z][a-z0-9_]{0,62}`, has a kind, and appears once.       |
| The ten standard fields are present, with their kinds.                              |
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
| A userset that is granted, such as `group:finance#member`, is not a relation that reads from the parent.                       |
| A tuple to userset through another relation does not reach a relation that reads from the parent.                              |
| Each key of `conditions` is the `name` of its condition.                                                                       |

## Rules of permissions

| Rule                                                                                                         |
| ------------------------------------------------------------------------------------------------------------ |
| `read`, `update`, `delete` and `grant` are relations of the type.                                            |
| A type without parents has an empty `create`.                                                                |
| A type with parents has a `create` that is a relation of every parent type.                                  |
| Each `grantable` relation is a relation of the type with direct types.                                       |
| A non-empty `creator` is a `grantable` relation that takes `user` directly, without a condition or wildcard. |

## Rules of flows

| Rule                                                                                                  |
| ----------------------------------------------------------------------------------------------------- |
| A declaration with flows declares `pyrite.dev/Account`.                                               |
| A flow name matches `[a-z][a-z0-9-]{0,62}`, and no two flows have the same one.                       |
| A flow has an input, at least one stage and at least one completion, and every completion has a kind. |
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
