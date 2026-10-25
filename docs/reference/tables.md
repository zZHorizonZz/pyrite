# Tables

Pyrite stores what it knows in nine tables with the prefix `pyrite_`: the resources that your application made known,
its own keys and accounts, flows, and declarations. Your application's resources stay in your tables, which Pyrite
never reads. For how to create Pyrite's tables, refer to [Create the tables](../how-to/create-the-tables.md).

## Pyrite's tables

| Table                | Holds                                                        |
| -------------------- | ------------------------------------------------------------ |
| `pyrite_declaration` | Every declaration written, with its OpenFGA store and model  |
| `pyrite_node`        | Every known resource, with its type and its parent           |
| `pyrite_key`         | The resources of `pyrite.dev/Key`                            |
| `pyrite_key_version` | The versions of keys                                         |
| `pyrite_account`     | The resources of `pyrite.dev/Account`                        |
| `pyrite_identifier`  | The identifiers of accounts                                  |
| `pyrite_factor`      | The factors of accounts                                      |
| `pyrite_session`     | The sessions of accounts                                     |
| `pyrite_flow`        | The flows of the authentication service, while they run      |

The tables of keys, versions, accounts, identifiers, factors and sessions have the columns `uid`, `name`, `parent`,
`display_name`, `labels`, `revision`, `create_time`, `update_time`, `delete_time` and `purge_time`, and columns of
their own, such as a key's `algorithm` or a session's `token_mac`. For what the services return of them, refer to
[Pyrite's types](pyrite-types.md).

## pyrite_declaration

| Column        | Type        | Description                                          |
| ------------- | ----------- | ---------------------------------------------------- |
| `id`          | `TEXT(36)`  | The declaration's id, a UUID. The primary key        |
| `declaration` | `BYTES`     | The serialized `pyrite.schema.v1.Declaration`        |
| `store_id`    | `TEXT(26)`  | The OpenFGA store, the same for every declaration    |
| `model_id`    | `TEXT(26)`  | The OpenFGA authorization model of the declaration   |
| `create_time` | `TIMESTAMP` | When it was written. The latest is the one in effect |

## pyrite_node

| Column        | Type        | Description                                                                   |
| ------------- | ----------- | ----------------------------------------------------------------------------- |
| `name`        | `TEXT(255)` | The resource's name, such as `projects/ledger`. The primary key               |
| `type`        | `TEXT(255)` | The declared type, such as `acme.dev/Project`                                 |
| `parent`      | `TEXT(255)` | The name of the resource it is under; null for a resource at the top. Indexed |
| `create_time` | `TIMESTAMP` | When the resource became known                                                |

`CreateObject`, `CreateKey` and `CreateAccount` add a row. `DeleteObject` and `DeleteAccount` remove it.

## pyrite_flow

| Column             | Type        | Description                                                      |
| ------------------ | ----------- | ---------------------------------------------------------------- |
| `id`               | `TEXT(36)`  | The flow's id, in its name `flows/<id>`. The primary key         |
| `definition`       | `TEXT(63)`  | The name of the flow definition                                  |
| `account`          | `TEXT(255)` | The account, or empty when the flow names no account that exists |
| `identifier`       | `TEXT(255)` | The identifier that the flow started with                        |
| `revision`         | `BIGINT`    | One more after each answer that moves the flow on                |
| `stage`            | `INT`       | The index of the current stage                                   |
| `proofs`           | `JSON`      | What the answers of the current stage proved                     |
| `answers`          | `INT`       | The answers taken, against `maxAnswers`                          |
| `expire_time`      | `TIMESTAMP` | When the flow expires                                            |
| `code_mac`         | `BYTES`     | The MAC of the code that the flow sent                           |
| `code_expire_time` | `TIMESTAMP` | When the code expires                                            |
| `challenge`        | `BYTES`     | The WebAuthn challenge of a passkey answer                       |
| `factor`           | `TEXT(255)` | The factor that the flow enrols                                  |
| `request_id`       | `TEXT(64)`  | The id of the last answer, for retries                           |
| `response`         | `BYTES`     | The response to the last answer, returned again on a retry       |
| `done`             | `BOOLEAN`   | Whether the flow is done                                         |
