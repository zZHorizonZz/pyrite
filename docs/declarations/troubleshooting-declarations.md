# Troubleshooting declarations

The errors of writing and reading declarations, and of calls that need what the declaration lacks, the most frequent
first. Each code block shows the status of the call and its message. `WriteDeclaration` checks the whole declaration
before it writes anything, so a refused declaration leaves the latest one as it was. For every rule, see
[Declaration reference](declaration-reference.md).

## No declaration is written; write one with pyrite.schema.v1.Schema...

```text
FAILED_PRECONDITION: No declaration is written; write one with pyrite.schema.v1.Schema.WriteDeclaration.
```

**Cause:** A call of the access, cryptography or authentication service before the first declaration. Every call
reads the latest declaration, and the database has none, such as a new database or one whose
`pyrite_declaration` table was emptied.

**Fix:** Write the declaration with `WriteDeclaration` when the application starts, before other calls. See
[Get started](../get-started.md).

**Verify:** `ReadDeclaration` with an empty `id` gives the declaration.

## The relation ... of the type ... has directly related types exactly when its rewrite has this

```text
INVALID_ARGUMENT: The relation editor of the type acme.dev/Project has directly related types exactly when its rewrite has this.
```

**Cause:** The rewrite of the relation has `this`, anywhere in a union, an intersection or a difference, and
`metadata` lists no direct types for it; or `metadata` lists direct types for a relation without `this`.

**Fix:** Add the relation to `metadata` with its `directly_related_user_types`, or remove them when nothing is granted
the relation directly.

**Verify:** `WriteDeclaration` gives an id.

## The type ... needs the relation ... for its grant permission

```text
INVALID_ARGUMENT: The type acme.dev/Project needs the relation can_grant for its grant permission, but does not declare it.
```

**Cause:** `permissions.grant` names no relation of the type, or is empty. Every type has a grant permission.

**Fix:** Set `grant` to a relation of the type, such as `owner` or `admin`.

**Verify:** `WriteDeclaration` gives an id.

## The type ... is created with the relation ... on its parent

```text
INVALID_ARGUMENT: The type acme.dev/Project is created with the relation owner on its parent, but the type acme.dev/Organization does not declare it.
```

**Cause:** `permissions.create` names a relation that a parent type does not have, or is empty for a type with
parents. Pyrite checks it on the parent, so each parent type has it.

**Fix:** Set `create` to a relation that every parent type declares, such as `admin`.

**Verify:** `WriteDeclaration` gives an id.

## The type ... has no parents, so its create permission is empty

```text
INVALID_ARGUMENT: The type acme.dev/Organization has no parents, so its create permission is empty; anyone that the caller names creates one.
```

**Cause:** A type at the top has a `create` permission. A resource at the top has no parent to check it on.

**Fix:** Leave `create` empty, and decide in your application who creates the resources of the type.

**Verify:** `WriteDeclaration` gives an id.

## The type ... grants ..., which is not a relation of the type with direct types

```text
INVALID_ARGUMENT: The type acme.dev/Project grants can_view, which is not a relation of the type with direct types.
```

**Cause:** A `grantable` relation is not declared, or takes no grants: its rewrite has no `this`, such as a relation
that only reads other relations.

**Fix:** List only relations with `this` and direct types in `grantable`. Grant the relations that a computed
relation reads, such as `viewer` for `can_view`.

**Verify:** `WriteDeclaration` gives an id.

## The type ... grants ... to its creator, which has to be a grantable relation...

```text
INVALID_ARGUMENT: The type acme.dev/Project grants can_view to its creator, which has to be a grantable relation that takes a user without a condition.
```

**Cause:** The `creator` relation is not in `grantable`, or does not take `user` directly without a condition and a
wildcard.

**Fix:** Name a grantable relation whose `metadata` has `directly_related_user_types { type: "user" }`, such as
`owner`, or leave `creator` empty.

**Verify:** `WriteDeclaration` gives an id, and `CreateObject` grants the creator the relation.

## The relation ... reads ... from ..., but the type ... has no relation ...

```text
INVALID_ARGUMENT: The relation reader of the type acme.dev/Project reads auditor from parent, but the type acme.dev/Organization has no relation auditor.
```

**Cause:** A `tuple_to_userset` reads a relation that a type it reads from does not have: a parent type, for the
tupleset `parent`, or a direct type of the tupleset relation. For the direct type `user`, the message names the type
`user` when the declaration has no `pyrite.dev/Account`.

**Fix:** Declare the relation on each type that the rewrite reads from, or read a relation that they have.

**Verify:** `WriteDeclaration` gives an id.

## The relation ... reads ... from ..., which is not a relation of the type with direct types

```text
INVALID_ARGUMENT: The relation reader of the type acme.dev/Project reads member from can_view, which is not a relation of the type with direct types.
```

**Cause:** The tupleset of a `tuple_to_userset` is not `parent`, and not a relation of the type that takes grants.
The tupleset names the resources to read from, so it needs direct types.

**Fix:** Read through `parent`, or through a relation with `this` and direct types, such as `binding`.

**Verify:** `WriteDeclaration` gives an id.

## The relation ... reads ... from parent, but the type has no parents

```text
INVALID_ARGUMENT: The relation auditor of the type acme.dev/Organization reads admin from parent, but the type has no parents.
```

**Cause:** A type without parents reads a relation through `parent`. Pyrite adds the relation `parent` only to a type
with parents.

**Fix:** Remove the `tuple_to_userset` through `parent`, or give the type its parents.

**Verify:** `WriteDeclaration` gives an id.

## The relation ... names ..., which is not a relation of the type

```text
INVALID_ARGUMENT: The relation auditor of the type acme.dev/Project names accountant, which is not a relation of the type.
```

**Cause:** A `computed_userset` names a relation that the type does not declare, such as a typo.

**Fix:** Name a relation of the same type. To read a relation of another type, use a `tuple_to_userset`.

**Verify:** `WriteDeclaration` gives an id.

## The type ... grants ... to the type ..., which is not declared

```text
INVALID_ARGUMENT: The type acme.dev/Project grants auditor to the type folder, which is not declared.
```

**Cause:** A direct type in `metadata` is not the singular name of a declared type, nor `user`. A direct type names
the singular name, such as `group`, not the type name `acme.dev/Group`.

**Fix:** Name the singular name of a declared type, or declare the type.

**Verify:** `WriteDeclaration` gives an id.

## The type ... grants ... to ...#..., which is not a relation of that type

```text
INVALID_ARGUMENT: The type acme.dev/Project grants auditor to group#owner, which is not a relation of that type.
```

**Cause:** A direct type with a relation, such as `group#owner`, names a relation that the type does not declare.

**Fix:** Name a relation of that type, such as `group#member`.

**Verify:** `WriteDeclaration` gives an id.

## The type ... grants ... to user#..., but the type user has no relations

```text
INVALID_ARGUMENT: The type acme.dev/Project grants auditor to user#member, but the type user has no relations.
```

**Cause:** A direct type `user` with a relation. A grant goes to a user, to every user with a wildcard, or to the
members of a set of another type.

**Fix:** Remove the relation from the direct type `user`, or name a set of another type, such as `group#member`.

**Verify:** `WriteDeclaration` gives an id.

## The type ... grants ... with the condition ..., which is not declared

```text
INVALID_ARGUMENT: The type acme.dev/Project grants auditor with the condition on_weekdays, which is not declared.
```

**Cause:** A direct type names a condition that `conditions` does not have.

**Fix:** Declare the condition in `conditions`, under its name, or name a declared one.

**Verify:** `WriteDeclaration` gives an id.

## The condition ... is named ...

```text
INVALID_ARGUMENT: The condition not_expired is named expired.
```

**Cause:** The key of an entry of `conditions` differs from the `name` of its condition.

**Fix:** Give the condition the name of its key.

**Verify:** `WriteDeclaration` gives an id.

## The type ... has the parent type ..., which is not declared

```text
INVALID_ARGUMENT: The type acme.dev/Project has the parent type acme.dev/Folder, which is not declared.
```

**Cause:** A parent is not the name of a declared type, such as a typo, a singular name, or a type that the
declaration no longer has.

**Fix:** Name a declared type, such as `acme.dev/Organization`, or declare the parent type.

**Verify:** `WriteDeclaration` gives an id.

## The type ... declares the relation parent, which is Pyrite's

```text
INVALID_ARGUMENT: The type acme.dev/Project declares the relation parent, which is Pyrite's: it holds the resource that a resource is under.
```

**Cause:** The definition declares a relation named `parent`. Pyrite adds it to every type with parents.

**Fix:** Remove the relation, and declare the parent types in `parents`.

**Verify:** `WriteDeclaration` gives an id.

## The type ... has the singular name user, which is Pyrite's accounts'

```text
INVALID_ARGUMENT: The type acme.dev/Member has the singular name user, which is Pyrite's accounts'.
```

**Cause:** A type other than `pyrite.dev/Account` has the singular name `user`. Subjects such as `user:anne` are
accounts.

**Fix:** Give the type another singular name, or declare `pyrite.dev/Account` to give users relations.

**Verify:** `WriteDeclaration` gives an id.

## The type ... has the singular name ..., which another type has

```text
INVALID_ARGUMENT: The type acme.dev/Project has the singular name group, which another type has.
```

**Cause:** Two types have the same `definition.type`. OpenFGA names objects by it, so it is unique.

**Fix:** Give each type its own singular name.

**Verify:** `WriteDeclaration` gives an id.

## The type ... has the plural name ..., which another type has

```text
INVALID_ARGUMENT: The type acme.dev/Project has the plural name groups, which another type has.
```

**Cause:** Two types have the same `plural`. A name such as `groups/finance` finds its type by the plural. A type of
yours with the plural `keys` or `accounts` meets Pyrite's types, which take them.

**Fix:** Give each type its own plural.

**Verify:** `WriteDeclaration` gives an id.

## The type ... has the ... name ..., which does not match ...

```text
INVALID_ARGUMENT: The type acme.dev/Project has the plural name pro-jects, which does not match [a-z][a-zA-Z0-9]{0,62}.
```

**Cause:** A singular name, a plural or a relation name breaks its pattern. The message says which: `has the singular
name`, `has the plural name` or `has the relation`. A singular name and a relation name match `[a-z][a-z0-9_]{0,49}`.

**Fix:** Rename it, such as `projects` for the plural and `can_view` for a relation.

**Verify:** `WriteDeclaration` gives an id.

## The type ... has metadata for ..., which is not a relation

```text
INVALID_ARGUMENT: The type acme.dev/Project has metadata for auditor, which is not a relation.
```

**Cause:** `metadata` describes a relation that the definition does not declare, such as one that was removed.

**Fix:** Remove the entry from `metadata`, or declare the relation.

**Verify:** `WriteDeclaration` gives an id.

## The type ... is Pyrite's own, which names its resources itself

```text
INVALID_ARGUMENT: The type pyrite.dev/Key is Pyrite's own, which names its resources itself; its declaration leaves plural empty.
```

**Cause:** `pyrite.dev/Key` or `pyrite.dev/Account` has a `plural`. Pyrite names their resources `keys/<id>` and
`accounts/<id>`.

**Fix:** Leave `plural` empty.

**Verify:** `WriteDeclaration` gives an id.

## The type ... is Pyrite's; a declaration does not declare it

```text
INVALID_ARGUMENT: The type pyrite.dev/Project is Pyrite's; a declaration does not declare it.
```

**Cause:** A type name starts with `pyrite.dev/` and is not `pyrite.dev/Key` or `pyrite.dev/Account`.

**Fix:** Name your types under a domain of your own, such as `acme.dev/Project`.

**Verify:** `WriteDeclaration` gives an id.

## The type ... lacks the relation ..., which Pyrite checks

```text
INVALID_ARGUMENT: The type pyrite.dev/Key lacks the relation verifier, which Pyrite checks.
```

**Cause:** `pyrite.dev/Key` lacks one of `admin`, `viewer`, `encrypter`, `decrypter`, `signer` and `verifier`, or
`pyrite.dev/Account` lacks one of `owner`, `admin` and `viewer`.

**Fix:** Declare the relation. See [Pyrite's types](pyrite-types.md).

**Verify:** `WriteDeclaration` gives an id.

## The relation owner of pyrite.dev/Account takes a user directly

```text
INVALID_ARGUMENT: The relation owner of pyrite.dev/Account takes a user directly; Pyrite grants it to each account on itself.
```

**Cause:** The `owner` of `pyrite.dev/Account` does not take `user` directly, or takes it only with a condition or a
wildcard.

**Fix:** Add `directly_related_user_types { type: "user" }` to the `metadata` of `owner`.

**Verify:** `WriteDeclaration` gives an id.

## The declaration has keys, whose material a root key seals...

```text
FAILED_PRECONDITION: The declaration has keys, whose material a root key seals, but the options name no root key.
```

**Cause:** The declaration has `pyrite.dev/Key`, and the key options of this Pyrite have no root key.

**Fix:** Give the key options a root key with `setRoot`, and give every Pyrite on the database the same options. See
[Serve the services](../deployment/serve-the-services.md).

**Verify:** `WriteDeclaration` gives an id.

## The declaration has accounts, which need the type pyrite.dev/Key...

```text
FAILED_PRECONDITION: The declaration has accounts, which need the type pyrite.dev/Key and the options of authentication; these lack macKey, issuer.
```

**Cause:** The declaration has `pyrite.dev/Account`, and the authentication options lack what the message names:
`macKey`, `encryptionKey`, `issuer`, or the `id` and `name` of `relyingPartyOptions`. A declaration without
`pyrite.dev/Key` ends the message with `it has no keys.` instead.

**Fix:** Declare `pyrite.dev/Key`, and set what the message names in the authentication options. See
[Options](../deployment/options.md).

**Verify:** `WriteDeclaration` gives an id.

## The declaration has exchanges but no accounts

```text
INVALID_ARGUMENT: The declaration has exchanges but no accounts; declare the type pyrite.dev/Account.
```

**Cause:** The declaration has `exchanges`, and no `pyrite.dev/Account`. An exchange works on accounts.

**Fix:** Declare `pyrite.dev/Account`, or remove the exchanges.

**Verify:** `WriteDeclaration` gives an id.

## The exchange ... starts from an identifier, but the declaration has no identifier kinds

```text
INVALID_ARGUMENT: The exchange sign-in starts from an identifier, but the declaration has no identifier kinds.
```

**Cause:** The input of the exchange is `IDENTIFIER` or `SUBJECT_IDENTIFIER`, and the declaration has no
`identifier_kinds`. Pyrite has no kinds of its own.

**Fix:** Declare the kinds that people sign in with, such as `email`. See
[Declaration reference](declaration-reference.md#identifierkind).

**Verify:** `WriteDeclaration` gives an id.

## The exchange ... sends a code to an identifier, but no identifier kind...

```text
INVALID_ARGUMENT: The exchange verify sends a code to an identifier, but no identifier kind of the declaration receives codes.
```

**Cause:** A stage of the exchange has `identifier: true`, and no declared kind has `receives_codes: true`.

**Fix:** Set `receives_codes: true` on each kind that your application can send a code to, such as `email` or
`phone`.

**Verify:** `WriteDeclaration` gives an id.

## The exchange ... completes with ... without the stages and the input that it needs

```text
INVALID_ARGUMENT: The exchange sign-in completes with ISSUE_SESSION without the stages and the input that it needs.
```

**Cause:** A completion lacks what it needs. `ISSUE_SESSION` needs the input `IDENTIFIER` and a stage with factors.
`MARK_VERIFIED` needs the input `SUBJECT_IDENTIFIER` and a stage with `identifier`. `ACTIVATE_FACTOR` needs a stage
that enrols. `END_SESSIONS` needs a proven account.

**Fix:** Add the stage or change the input that the completion needs, or move the completion to an exchange of its own.
See [Declaration reference](declaration-reference.md#rules-of-exchanges).

**Verify:** `WriteDeclaration` gives an id.

## The exchange ... enrols a factor twice, or before the account is proven...

```text
INVALID_ARGUMENT: The exchange takeover enrols a factor twice, or before the account is proven by the input SUBJECT or by an earlier stage.
```

**Cause:** An exchange that starts from an identifier enrols a factor before a stage proves the account, or an exchange
has two stages that enrol.

**Fix:** Start the exchange from `SUBJECT`, or prove the account first, such as with a stage with `identifier: true`.
Enrol one factor in each exchange.

**Verify:** `WriteDeclaration` gives an id.

## The exchange ... enrols a factor in a stage that requires more

```text
INVALID_ARGUMENT: The exchange mixed enrols a factor in a stage that requires more; an enrolment is a stage of its own.
```

**Cause:** A stage with `enrol` also has `factors`, `phishing_resistant` or `identifier`.

**Fix:** Split it into a stage that proves and a stage that enrols.

**Verify:** `WriteDeclaration` gives an id.

## The exchange ... has a stage that requires nothing

```text
INVALID_ARGUMENT: The exchange nothing has a stage that requires nothing.
```

**Cause:** A stage has no `factors`, no `phishing_resistant`, no `identifier` and no `enrol`.

**Fix:** Remove the stage, or give it what it requires.

**Verify:** `WriteDeclaration` gives an id.

## The exchange ... requires ... factors; a stage requires 0 to 3

```text
INVALID_ARGUMENT: The exchange many requires 4 factors; a stage requires 0 to 3.
```

**Cause:** A stage has `factors` below 0 or above 3.

**Fix:** Require at most 3 distinct factors in one stage.

**Verify:** `WriteDeclaration` gives an id.

## The exchange ... has no input, or a completion with no kind

```text
INVALID_ARGUMENT: The exchange odd has no input, or a completion with no kind.
```

**Cause:** The exchange has no `input`, or a completion of `COMPLETION_UNSPECIFIED`.

**Fix:** Set the `input` and the kind of each completion.

**Verify:** `WriteDeclaration` gives an id.

## The exchange ... has no stages or no completions

```text
INVALID_ARGUMENT: The exchange empty has no stages or no completions.
```

**Cause:** The exchange has no `stages`, or no `completes`.

**Fix:** Give the exchange at least one stage and one completion.

**Verify:** `WriteDeclaration` gives an id.

## The exchange ... has a name that does not match ...

```text
INVALID_ARGUMENT: The exchange Sign_In has a name that does not match [a-z][a-z0-9-]{0,62}.
```

**Cause:** The name of the exchange has capitals, underscores or other characters that the pattern does not take.

**Fix:** Name the exchange with lowercase letters, digits and hyphens, such as `sign-in`.

**Verify:** `WriteDeclaration` gives an id.

## The match of the identifier kind ... is not a CEL expression...

```text
INVALID_ARGUMENT: The match of the identifier kind badge is not a CEL expression over the string value that gives a bool: <the error of CEL>
```

**Cause:** The `match` of a kind does not compile, or gives something other than a `bool`, such as `value` alone. The
same message with `normalize` and `string` is about a `normalize` that does not give a string.

**Fix:** Write `match` as a condition on `value`, such as `value.matches(r'^[0-9]{6}$')`, and `normalize` as a string,
such as `value.trim()`. The message ends with the error of CEL, which names the position.

**Verify:** `WriteDeclaration` gives an id.

## The identifier kind ... is not named like ...

```text
INVALID_ARGUMENT: The identifier kind Badge is not named like [a-z][a-z0-9-]{0,62}.
```

**Cause:** The name of a kind has capitals or characters that the pattern does not take. A kind with an empty `match`
fails with `has no match.` instead.

**Fix:** Name the kind with lowercase letters, digits and hyphens, such as `employee-id`, and give it a `match`.

**Verify:** `WriteDeclaration` gives an id.

## The ... is declared twice

```text
INVALID_ARGUMENT: The type acme.dev/Project is declared twice.
```

**Cause:** Two types, two identifier kinds or two exchanges have the same name. The message starts with `The type`,
`The identifier kind` or `The exchange`.

**Fix:** Keep one of them, or rename one.

**Verify:** `WriteDeclaration` gives an id.

## A type has no name

```text
INVALID_ARGUMENT: A type has no name.
```

**Cause:** A `types` entry has an empty `name`.

**Fix:** Name the type, such as `acme.dev/Project`.

**Verify:** `WriteDeclaration` gives an id.

## The declaration has no keys; declare the type pyrite.dev/Key

```text
FAILED_PRECONDITION: The declaration has no keys; declare the type pyrite.dev/Key.
```

**Cause:** A call of the cryptography service, and the latest declaration has no `pyrite.dev/Key`. The authentication
service fails with `The declaration has no accounts; declare the type pyrite.dev/Account.` without
`pyrite.dev/Account`, and an exchange with `The declaration has no accounts, so it has no exchanges.`

**Fix:** Declare the type, with the relations that its service checks. See [Pyrite's types](pyrite-types.md).

**Verify:** The call gives its result.

## The declaration ... does not exist

```text
NOT_FOUND: The declaration 0f8e4bd2-6b7c-4b8e-9c55-2f8f3c7a1d10 does not exist.
```

**Cause:** `ReadDeclaration` of an `id` that no declaration has. Before the first declaration, `ReadDeclaration` with
an empty `id` fails with `No declaration is written.`

**Fix:** Read the `id` that `WriteDeclaration` gave, or an empty `id` for the latest.

**Verify:** `ReadDeclaration` gives the declaration.

## Related

- [Declaration reference](declaration-reference.md)
- [Declare a type](declare-a-type.md)
- [Troubleshooting access](../access/troubleshooting-access.md)
