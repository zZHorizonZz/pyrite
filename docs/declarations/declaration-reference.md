# Declaration reference

A `pyrite.schema.v1.Declaration` declares the types, conditions, identifier kinds and exchanges of your application.
`WriteDeclaration` checks it against the rules on this page and fails with `INVALID_ARGUMENT` at the first rule that it
breaks, with the message in the rule's row. In the messages, `<type>` is the name of the type, such as
`acme.dev/Project`. To write a declaration, see [Declare a type](declare-a-type.md).

## The schema service

| Method             | Request                    | Response                                                                              |
| ------------------ | -------------------------- | ------------------------------------------------------------------------------------- |
| `WriteDeclaration` | `declaration`              | `id`, the new id of the declaration, which becomes the latest                         |
| `ReadDeclaration`  | `id`; empty for the latest | `id`, `declaration` as it was written, and `create_time`, when the database stored it |

`ReadDeclaration` fails with `NOT_FOUND` before the first declaration, and for an `id` that no declaration has.
Neither method takes a subject.

## Declaration

| Field              | Type                                      | Description                                                   |
| ------------------ | ----------------------------------------- | ------------------------------------------------------------- |
| `types`            | repeated `Type`                           | The types of resources                                        |
| `conditions`       | map of `string` to `openfga.v1.Condition` | The conditions that relations take, by name                   |
| `identifier_kinds` | repeated `IdentifierKind`                 | The kinds of identifiers, in the order that Pyrite tries them |
| `exchanges`        | repeated `ExchangeDefinition`             | The exchanges of the authentication service                   |

A condition is OpenFGA's: a `name`, a CEL `expression`, and its `parameters` with their types, such as
`TYPE_NAME_TIMESTAMP`.

## Type

| Field         | Type                        | Description                                                                                                        |
| ------------- | --------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `name`        | `string`                    | The name of the type, such as `acme.dev/Project`                                                                   |
| `plural`      | `string`                    | The first segment of the names of its resources, such as `projects`; empty for Pyrite's types                      |
| `parents`     | repeated `string`           | The types that its resources sit under; none for a type at the top                                                 |
| `definition`  | `openfga.v1.TypeDefinition` | The OpenFGA type: its singular name in `type`, its relations, and in `metadata` the types that each relation takes |
| `permissions` | `Permissions`               | The relations that Pyrite checks                                                                                   |
| `grantable`   | repeated `string`           | The relations that the access service grants and revokes                                                           |
| `creator`     | `string`                    | The relation that the creator of a resource is granted on it; empty for none                                       |

Pyrite adds the relation `parent` to the OpenFGA type of each type that has parents. It takes the singular names of
the parent types directly. `pyrite.dev/Key` and `pyrite.dev/Account` have their own requirements; see
[Pyrite's types](pyrite-types.md).

## Permissions

| Field    | Checked on   | Description                                                                                                         |
| -------- | ------------ | ------------------------------------------------------------------------------------------------------------------- |
| `create` | The parent   | The relation that makes a resource known under the parent, or creates a key there; empty for a type without parents |
| `grant`  | The resource | The relation that grants, revokes and lists grants, forgets the resource, and checks other principals               |

## IdentifierKind

| Field            | Type     | Description                                                                                           |
| ---------------- | -------- | ----------------------------------------------------------------------------------------------------- |
| `name`           | `string` | The name of the kind, such as `email` or `employee-id`                                                |
| `match`          | `string` | A CEL expression over `value` that is `true` when the value is of this kind                           |
| `normalize`      | `string` | A CEL expression over `value` that gives the value that Pyrite stores; empty keeps the value as given |
| `receives_codes` | `bool`   | Whether your application sends codes to identifiers of this kind                                      |

Both expressions take one variable, `value`, the string as the caller gave it. They can use CEL's standard functions
and macros and its strings extension, such as `trim()`, `lowerAscii()`, `replace()` and `matches()`, and a
comprehension runs at most 1000 iterations. For example:

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

A value is of a kind when it meets `match`, and its normalized value meets `match` too. A value whose `match` fails to
evaluate is of no kind. A value without a named kind takes the first kind whose `match` it meets.

## ExchangeDefinition

| Field         | Type                                     | Description                                                                                                                  |
| ------------- | ---------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `name`        | `string`                                 | The name of the exchange, such as `sign-in`                                                                                  |
| `input`       | `ExchangeDefinition.Input`               | What the exchange starts from                                                                                                |
| `stages`      | repeated `Stage`                         | What the exchange has to prove, stage by stage, in order                                                                     |
| `completes`   | repeated `ExchangeDefinition.Completion` | What the exchange does when it ends, in order                                                                                |
| `email_match` | `ExchangeDefinition.EmailMatch`          | What an unlinked provider account of a verified email address does; unset, `SIGN_IN` or `CONFIRM`; only for the input `NONE` |

| Input                | The exchange starts from                                     |
| -------------------- | ------------------------------------------------------------ |
| `IDENTIFIER`         | An identifier, such as the email address that a person typed |
| `SUBJECT`            | The account that calls                                       |
| `SUBJECT_IDENTIFIER` | The account that calls, and one of its identifiers           |
| `NONE`               | Nothing: a provider answer names the account                 |

| Completion        | What the exchange does when it ends                                                                     |
| ----------------- | ------------------------------------------------------------------------------------------------------- |
| `ISSUE_SESSION`   | Issues a session to the account                                                                         |
| `MARK_VERIFIED`   | Marks the identifier of the exchange verified                                                           |
| `ACTIVATE_FACTOR` | Activates the factor that the exchange enrolled                                                         |
| `END_SESSIONS`    | Ends the other sessions of the account                                                                  |
| `CREATE_ACCOUNT`  | Creates an account for a provider account that no account links, and links it; first, before the others |

## Stage

| Field                | Type           | Default | Description                                                             |
| -------------------- | -------------- | ------- | ----------------------------------------------------------------------- |
| `factors`            | `int32`        | `0`     | The number of distinct factors that the stage requires, `0` to `3`      |
| `phishing_resistant` | `bool`         | `false` | Whether one of the factors is a passkey                                 |
| `identifier`         | `bool`         | `false` | Whether the stage requires a code sent to the identifier                |
| `enrol`              | `Stage.Factor` | unset   | `PASSWORD`, `TOTP`, `PASSKEY`, `RECOVERY_CODES` or `PROVIDER`, to enrol |

## Rules of types

| Rule                                                                                                         | Message                                                                                                   |
| ------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------- |
| A type has a `name`.                                                                                         | `A type has no name.`                                                                                     |
| No two types have the same name.                                                                             | `The type <type> is declared twice.`                                                                      |
| A name that starts with `pyrite.dev/` is `pyrite.dev/Key` or `pyrite.dev/Account`.                           | `The type <type> is Pyrite's; a declaration does not declare it.`                                         |
| `pyrite.dev/Key` and `pyrite.dev/Account` leave `plural` empty.                                              | `The type <type> is Pyrite's own, which names its resources itself; its declaration leaves plural empty.` |
| The singular name, `definition.type`, matches `[a-z][a-z0-9_]{0,49}`.                                        | `The type <type> has the singular name <singular>, which does not match [a-z][a-z0-9_]{0,49}.`            |
| No two types have the same singular name.                                                                    | `The type <type> has the singular name <singular>, which another type has.`                               |
| Only `pyrite.dev/Account` has the singular name `user`.                                                      | `The type <type> has the singular name user, which is Pyrite's accounts'.`                                |
| `plural` matches `[a-z][a-zA-Z0-9]{0,62}`.                                                                   | `The type <type> has the plural name <plural>, which does not match [a-z][a-zA-Z0-9]{0,62}.`              |
| No two types have the same plural. `pyrite.dev/Key` takes `keys`, and `pyrite.dev/Account` takes `accounts`. | `The type <type> has the plural name <plural>, which another type has.`                                   |
| Each parent is a declared type.                                                                              | `The type <type> has the parent type <parent>, which is not declared.`                                    |

## Rules of relations

| Rule                                                                                                                                                                                                               | Message                                                                                                                           |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------- |
| A relation name matches `[a-z][a-z0-9_]{0,49}`.                                                                                                                                                                    | `The type <type> has the relation <relation>, which does not match [a-z][a-z0-9_]{0,49}.`                                         |
| No relation is named `parent`.                                                                                                                                                                                     | `The type <type> declares the relation parent, which is Pyrite's: it holds the resource that a resource is under.`                |
| `metadata` describes only relations of the type.                                                                                                                                                                   | `The type <type> has metadata for <name>, which is not a relation.`                                                               |
| A relation has direct types in `metadata` exactly when its rewrite has `this`, anywhere in it.                                                                                                                     | `The relation <relation> of the type <type> has directly related types exactly when its rewrite has this.`                        |
| A direct type `user` names no relation.                                                                                                                                                                            | `The type <type> grants <relation> to user#<name>, but the type user has no relations.`                                           |
| Any other direct type is a declared singular name.                                                                                                                                                                 | `The type <type> grants <relation> to the type <singular>, which is not declared.`                                                |
| A direct type with a relation, such as `group#member`, names a relation of that type.                                                                                                                              | `The type <type> grants <relation> to <singular>#<name>, which is not a relation of that type.`                                   |
| A direct type's condition is a declared condition.                                                                                                                                                                 | `The type <type> grants <relation> with the condition <condition>, which is not declared.`                                        |
| A `computed_userset` names a relation of the same type.                                                                                                                                                            | `The relation <relation> of the type <type> names <name>, which is not a relation of the type.`                                   |
| A `tuple_to_userset` through `parent` is on a type with parents.                                                                                                                                                   | `The relation <relation> of the type <type> reads <name> from parent, but the type has no parents.`                               |
| A `tuple_to_userset` through another relation goes through a relation of the type with direct types.                                                                                                               | `The relation <relation> of the type <type> reads <name> from <tupleset>, which is not a relation of the type with direct types.` |
| Each type that a `tuple_to_userset` reads from, each parent type or each direct type of the tupleset, has the relation that it reads. The direct type `user` is `pyrite.dev/Account`, when the declaration has it. | `The relation <relation> of the type <type> reads <name> from <tupleset>, but the type <target> has no relation <name>.`          |
| Each key of `conditions` is the `name` of its condition.                                                                                                                                                           | `The condition <key> is named <name>.`                                                                                            |

## Rules of permissions

| Rule                                                                                                           | Message                                                                                                                        |
| -------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `grant` is a relation of the type.                                                                             | `The type <type> needs the relation <grant> for its grant permission, but does not declare it.`                                |
| A type without parents has an empty `create`.                                                                  | `The type <type> has no parents, so its create permission is empty; anyone that the caller names creates one.`                 |
| A type with parents has a `create` that is a relation of every parent type.                                    | `The type <type> is created with the relation <create> on its parent, but the type <parent> does not declare it.`              |
| Each `grantable` relation is a relation of the type with direct types.                                         | `The type <type> grants <relation>, which is not a relation of the type with direct types.`                                    |
| A non-empty `creator` is a `grantable` relation that takes `user` directly, without a condition or a wildcard. | `The type <type> grants <creator> to its creator, which has to be a grantable relation that takes a user without a condition.` |
| `pyrite.dev/Key` has the relations `viewer`, `admin`, `encrypter`, `decrypter`, `signer` and `verifier`.       | `The type pyrite.dev/Key lacks the relation <relation>, which Pyrite checks.`                                                  |
| `pyrite.dev/Account` has the relations `owner`, `viewer` and `admin`.                                          | `The type pyrite.dev/Account lacks the relation <relation>, which Pyrite checks.`                                              |
| The `owner` of `pyrite.dev/Account` takes `user` directly, without a condition or a wildcard.                  | `The relation owner of pyrite.dev/Account takes a user directly; Pyrite grants it to each account on itself.`                  |

## Rules of identifier kinds

| Rule                                                                                                | Message                                                                                                                  |
| --------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| A kind name matches `[a-z][a-z0-9-]{0,62}`.                                                         | `The identifier kind <kind> is not named like [a-z][a-z0-9-]{0,62}.`                                                     |
| A kind has a non-empty `match`.                                                                     | `The identifier kind <kind> has no match.`                                                                               |
| `match` compiles as a CEL expression over the string `value` that gives a `bool`.                   | `The match of the identifier kind <kind> is not a CEL expression over the string value that gives a bool: <cause>`       |
| A non-empty `normalize` compiles as a CEL expression over the string `value` that gives a `string`. | `The normalize of the identifier kind <kind> is not a CEL expression over the string value that gives a string: <cause>` |
| No two kinds have the same name.                                                                    | `The identifier kind <kind> is declared twice.`                                                                          |

## Rules of exchanges

| Rule                                                                                                                                  | Message                                                                                                                       |
| ------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| No two exchanges have the same name.                                                                                                  | `The exchange <exchange> is declared twice.`                                                                                  |
| A declaration with exchanges declares `pyrite.dev/Account`.                                                                           | `The declaration has exchanges but no accounts; declare the type pyrite.dev/Account.`                                         |
| An exchange name matches `[a-z][a-z0-9-]{0,62}`.                                                                                      | `The exchange <exchange> has a name that does not match [a-z][a-z0-9-]{0,62}.`                                                |
| An exchange has an input, and each completion has a kind.                                                                             | `The exchange <exchange> has no input, or a completion with no kind.`                                                         |
| An exchange has at least one stage and at least one completion.                                                                       | `The exchange <exchange> has no stages or no completions.`                                                                    |
| An exchange whose input is `IDENTIFIER` or `SUBJECT_IDENTIFIER` needs an identifier kind.                                             | `The exchange <exchange> starts from an identifier, but the declaration has no identifier kinds.`                             |
| An exchange with a stage with `identifier` needs an identifier kind with `receives_codes`.                                            | `The exchange <exchange> sends a code to an identifier, but no identifier kind of the declaration receives codes.`            |
| A stage requires 0 to 3 factors.                                                                                                      | `The exchange <exchange> requires <factors> factors; a stage requires 0 to 3.`                                                |
| A stage that enrols requires nothing else.                                                                                            | `The exchange <exchange> enrols a factor in a stage that requires more; an enrolment is a stage of its own.`                  |
| A stage requires at least one thing.                                                                                                  | `The exchange <exchange> has a stage that requires nothing.`                                                                  |
| An exchange enrols at most once, after the account is proven: by the input `SUBJECT` or `SUBJECT_IDENTIFIER`, or by an earlier stage. | `The exchange <exchange> enrols a factor twice, or before the account is proven by the input SUBJECT or by an earlier stage.` |
| `ISSUE_SESSION` needs the input `IDENTIFIER` or `NONE` and a stage with factors or `phishing_resistant`.                              | `The exchange <exchange> completes with ISSUE_SESSION without the stages and the input that it needs.`                        |
| `MARK_VERIFIED` needs the input `SUBJECT_IDENTIFIER` and a stage with `identifier`.                                                   | `The exchange <exchange> completes with MARK_VERIFIED without the stages and the input that it needs.`                        |
| `ACTIVATE_FACTOR` needs a stage that enrols.                                                                                          | `The exchange <exchange> completes with ACTIVATE_FACTOR without the stages and the input that it needs.`                      |
| `CREATE_ACCOUNT` needs the input `NONE` and a stage with factors or `phishing_resistant`.                                             | `The exchange <exchange> completes with CREATE_ACCOUNT without the stages and the input that it needs.`                       |
| `email_match` needs the input `NONE`.                                                                                                 | `The exchange <exchange> matches emails, which only an exchange of the input NONE does.`                                      |
| `END_SESSIONS` needs a proven account: the input `SUBJECT` or `SUBJECT_IDENTIFIER`, or a stage with factors or `identifier`.          | `The exchange <exchange> completes with END_SESSIONS without the stages and the input that it needs.`                         |

## Rules of the options

These rules fail `WriteDeclaration` with `FAILED_PRECONDITION`, because the options of Pyrite break them, not the
declaration:

| Rule                                                                                                                                                               | Message                                                                                                                     |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------- |
| A declaration with `pyrite.dev/Key` needs a root key in the key options.                                                                                           | `The declaration has keys, whose material a root key seals, but the options name no root key.`                              |
| A declaration with `pyrite.dev/Account` declares `pyrite.dev/Key`.                                                                                                 | `The declaration has accounts, which need the type pyrite.dev/Key and the options of authentication; it has no keys.`       |
| A declaration with `pyrite.dev/Account` needs `macKey`, `encryptionKey`, `issuer`, and the `id` and `name` of `relyingPartyOptions` in the authentication options. | `The declaration has accounts, which need the type pyrite.dev/Key and the options of authentication; these lack <options>.` |

For the options, see [Options](../deployment/options.md).

## Related

- [About declarations](about-declarations.md)
- [Pyrite's types](pyrite-types.md)
- [Troubleshooting declarations](troubleshooting-declarations.md)
