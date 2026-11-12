# Tables

Pyrite stores what it knows in nine tables with the prefix `pyrite_`: the resources that your application made known,
its own keys and accounts, the flows of the authentication service, and the declarations. Your application's
resources stay in your tables, which Pyrite never reads. See [Create the tables](create-the-tables.md).

The types below are the portable ones; each database has its own, such as `uuid` and `jsonb` on PostgreSQL. A column
is `NOT NULL` unless its description says that it can be null.

## Pyrite's tables

| Table                | Holds                                                       |
| -------------------- | ----------------------------------------------------------- |
| `pyrite_declaration` | Every declaration written, with its OpenFGA store and model |
| `pyrite_node`        | Every known resource, with its type and its parent          |
| `pyrite_key`         | The resources of `pyrite.dev/Key`                           |
| `pyrite_key_version` | The versions of keys                                        |
| `pyrite_account`     | The resources of `pyrite.dev/Account`                       |
| `pyrite_identifier`  | The identifiers of accounts                                 |
| `pyrite_factor`      | The factors of accounts                                     |
| `pyrite_session`     | The sessions of accounts                                    |
| `pyrite_flow`        | The flows of the authentication service                     |

## What the database keeps

The migrations give the database the rules that keep the rows consistent:

- **Links.** A foreign key holds each link: an account and a key to their node, an identifier, a factor, a session
  and a flow to their account, a version to its key, and a node to its parent. Deleting a node deletes the rows of
  its resource, and the rows under those, in the same statement. A node with nodes under it is not deleted.
- **Kinds and states.** The kind of an account and of a factor, the state of a factor and of a version, and the purpose
  and algorithm of a key are enum columns, which take only their values.
- **Unique values.** The name of each resource is unique, the number of a version is unique per key, so that two
  rotations at once cannot make the same version, and the value of an identifier is unique per kind.
- **Revisions and times.** The database gives each row its `uid`, its first `revision` and its `create_time`, and
  each update of a resource a new `revision` and `update_time`.
- **The clock.** Every time that Pyrite stores or compares is the database's: the expiry of a session, a flow or a
  code, the next rotation of a key, and the purge time of what was removed. Several Pyrites on one database agree on
  every time.

## Columns of every resource table

`pyrite_key`, `pyrite_key_version`, `pyrite_account`, `pyrite_identifier`, `pyrite_factor` and `pyrite_session` have
these columns, followed by columns of their own:

| Column         | Type        | Description                                                                      |
| -------------- | ----------- | -------------------------------------------------------------------------------- |
| `uid`          | `UUID`      | A random id that the database gives. The primary key                             |
| `name`         | `TEXT(255)` | The name, such as `keys/payments`. Unique, compared byte by byte                 |
| `parent`       | `TEXT(255)` | The name of the resource it is under; can be null                                |
| `display_name` | `TEXT(255)` | Can be null                                                                      |
| `labels`       | `JSON`      | An object; `{}` by default                                                       |
| `revision`     | `BIGINT`    | `1` at the insert, and one more at each update                                   |
| `create_time`  | `TIMESTAMP` | When the row was inserted                                                        |
| `update_time`  | `TIMESTAMP` | When the row was last updated                                                    |
| `delete_time`  | `TIMESTAMP` | When the resource was removed, such as an ended session; null while it is in use |
| `purge_time`   | `TIMESTAMP` | When `Purge` may delete the row; can be null                                     |

The `parent` of a version, an identifier, a factor and a session is a foreign key to its key or its account, and is
indexed.

## pyrite_declaration

| Column        | Type        | Description                                                                  |
| ------------- | ----------- | ---------------------------------------------------------------------------- |
| `id`          | `TEXT(36)`  | The declaration's id, a UUID. The primary key                                |
| `declaration` | `BYTES`     | The serialized `pyrite.schema.v1.Declaration`                                |
| `store_id`    | `TEXT(26)`  | The OpenFGA store of the grants, the same for every declaration              |
| `model_id`    | `TEXT(26)`  | The authorization model that the declaration wrote to the store              |
| `create_time` | `TIMESTAMP` | When it was written. Indexed; the latest is the one that every service reads |

## pyrite_node

| Column        | Type        | Description                                                                   |
| ------------- | ----------- | ----------------------------------------------------------------------------- |
| `name`        | `TEXT(255)` | The resource's name, such as `projects/ledger`. The primary key               |
| `type`        | `TEXT(255)` | The declared type, such as `acme.dev/Project`                                 |
| `parent`      | `TEXT(255)` | The name of the resource it is under; null for a resource at the top. Indexed |
| `create_time` | `TIMESTAMP` | When the resource became known                                                |

`CreateObject`, `CreateKey` and `CreateAccount` add a row. `DeleteObject` and `DeleteAccount` remove it, and with it
the row of the key or the account of that name.

## pyrite_key

| Column               | Type        | Description                                                                       |
| -------------------- | ----------- | --------------------------------------------------------------------------------- |
| `purpose`            | Enum        | `ENCRYPTION`, `MAC` or `SIGNING`                                                  |
| `algorithm`          | Enum        | `AES256_GCM`, `HMAC_SHA256`, `RS256`, `ES256` or `EDDSA`                          |
| `rotation_period`    | `BIGINT`    | How often the key rotates on its own, in seconds; `0` for never                   |
| `next_rotation_time` | `TIMESTAMP` | When the key rotates next; null when it does not rotate on its own                |
| `primary_version`    | `TEXT(255)` | The name of the version that encrypts and signs, such as `keyVersions/payments-2` |

The `name` of a key is a foreign key to its node. Its `parent` is the name of the resource that it is under, and is
indexed.

## pyrite_key_version

| Column            | Type     | Description                                                                    |
| ----------------- | -------- | ------------------------------------------------------------------------------ |
| `number`          | `BIGINT` | `1` for the first version of the key, and one more for each after it           |
| `state`           | Enum     | `ENABLED` or `DISABLED`                                                        |
| `algorithm`       | Enum     | The algorithm of its key                                                       |
| `public_key`      | `JSON`   | The public key as a JWK, for a version of a signing key; null for the others   |
| `sealed`          | `BYTES`  | The material of the version, sealed by the root key                            |
| `public_key_info` | `BYTES`  | The X.509 SubjectPublicKeyInfo of a signing key's version; null for the others |

`parent` and `number` are unique together.

## pyrite_account

| Column | Type | Description          |
| ------ | ---- | -------------------- |
| `kind` | Enum | `HUMAN` or `SERVICE` |

The `name` of an account is a foreign key to its node.

## pyrite_identifier

| Column     | Type        | Description                                                              |
| ---------- | ----------- | ------------------------------------------------------------------------ |
| `kind`     | `TEXT(16)`  | The name of its identifier kind in the declaration, such as `email`      |
| `value`    | `TEXT(320)` | The value as the kind normalized it. Unique with `kind`                  |
| `verified` | `BOOLEAN`   | Whether a flow verified it with a code                                   |
| `primary`  | `BOOLEAN`   | Whether the account prefers it among its identifiers; the caller sets it |

## pyrite_factor

| Column          | Type        | Description                                                                                                          |
| --------------- | ----------- | -------------------------------------------------------------------------------------------------------------------- |
| `kind`          | Enum        | `PASSWORD`, `TOTP`, `PASSKEY` or `RECOVERY_CODES`                                                                    |
| `state`         | Enum        | `PENDING` while a flow enrols it, then `ACTIVE`                                                                      |
| `secret`        | `BYTES`     | The password's Argon2id hash, the TOTP secret encrypted, the passkey's credential, or the MACs of the recovery codes |
| `credential_id` | `TEXT(255)` | A passkey's credential id; empty for the others. Indexed                                                             |
| `counter`       | `BIGINT`    | A passkey's signature counter, or the last TOTP step that signed in                                                  |
| `user_verified` | `BOOLEAN`   | Whether a passkey verified its user when it was registered                                                           |

A pending factor has a `purge_time` of `flowLifetime` after its enrolment started.

## pyrite_session

| Column             | Type        | Description                                                  |
| ------------------ | ----------- | ------------------------------------------------------------ |
| `token_mac`        | `BYTES`     | The MAC of the secret of the session token                   |
| `expire_time`      | `TIMESTAMP` | When the session ends, `sessionLifetime` after it was issued |
| `idle_expire_time` | `TIMESTAMP` | When the session ends unless it authenticates before         |

## pyrite_flow

A flow is not a resource, and its table has none of the columns of a resource table.

| Column             | Type        | Description                                                             |
| ------------------ | ----------- | ----------------------------------------------------------------------- |
| `id`               | `TEXT(36)`  | The flow's id, in its name `flows/<id>`. The primary key                |
| `definition`       | `TEXT(63)`  | The name of the flow definition                                         |
| `account`          | `TEXT(255)` | The account; null when the flow names no account that exists            |
| `identifier`       | `TEXT(255)` | The identifier that the flow started with; can be null                  |
| `revision`         | `BIGINT`    | One more after each answer that moves the flow on                       |
| `stage`            | `INT`       | The index of the current stage                                          |
| `proofs`           | `JSON`      | What the answers of the current stage proved                            |
| `answers`          | `INT`       | The wrong answers taken, against `maxAnswers`                           |
| `expire_time`      | `TIMESTAMP` | When the flow expires                                                   |
| `code_mac`         | `BYTES`     | The MAC of the code that the flow sent; can be null                     |
| `code_expire_time` | `TIMESTAMP` | When the code expires; can be null                                      |
| `challenge`        | `BYTES`     | The WebAuthn challenge of a passkey answer; can be null                 |
| `factor`           | `TEXT(255)` | The factor that the flow enrols; can be null                            |
| `request_id`       | `TEXT(64)`  | The id of the last answer, for retries; can be null                     |
| `response`         | `BYTES`     | The response to the last answer, returned again on a retry; can be null |
| `done`             | `BOOLEAN`   | Whether the flow is done; a done flow takes no answers                  |

`account` is a foreign key to the account, so deleting an account deletes its flows.

## Related

- [Create the tables](create-the-tables.md)
- [Pyrite's types](../declarations/pyrite-types.md)
- [Leadseal](https://github.com/zZHorizonZz/leadseal), which generates the migrations of these tables
