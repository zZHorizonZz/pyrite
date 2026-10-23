# Tables

Pyrite stores its own resources, flows and declarations in eight tables with the prefix `pyrite_`. Your types'
resources are in your tables, with the columns on this page. For how to create both, refer to
[Create the tables](../how-to/create-the-tables.md).

## Pyrite's tables

| Table                | Holds                                                       |
| -------------------- | ----------------------------------------------------------- |
| `pyrite_declaration` | Every declaration written, with its OpenFGA store and model |
| `pyrite_key`         | The resources of `pyrite.dev/Key`                           |
| `pyrite_key_version` | The resources of `pyrite.dev/KeyVersion`                    |
| `pyrite_account`     | The resources of `pyrite.dev/Account`                       |
| `pyrite_identifier`  | The resources of `pyrite.dev/Identifier`                    |
| `pyrite_factor`      | The resources of `pyrite.dev/Factor`                        |
| `pyrite_session`     | The resources of `pyrite.dev/Session`                       |
| `pyrite_flow`        | The flows of the authentication service, while they run     |

The resource tables have the standard columns and the fields of their type. For the fields, refer to
[Pyrite's types](pyrite-types.md).

## pyrite_declaration

| Column        | Type        | Description                                          |
| ------------- | ----------- | ---------------------------------------------------- |
| `id`          | `TEXT(36)`  | The declaration's id, a UUID. The primary key        |
| `declaration` | `BYTES`     | The serialized `pyrite.schema.v1.Declaration`        |
| `store_id`    | `TEXT(26)`  | The OpenFGA store, the same for every declaration    |
| `model_id`    | `TEXT(26)`  | The OpenFGA authorization model of the declaration   |
| `create_time` | `TIMESTAMP` | When it was written. The latest is the one in effect |

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

## Columns of your tables

Each table of a declared type has the standard columns, and one column per field of the type's own. Use the column
type of each field's kind:

| Kind        | PostgreSQL         | MariaDB       | SQL Server       | SQLite    |
| ----------- | ------------------ | ------------- | ---------------- | --------- |
| `STRING`    | `varchar(n)`       | `VARCHAR(n)`  | `NVARCHAR(n)`    | `TEXT`    |
| `INTEGER`   | `bigint`           | `BIGINT`      | `BIGINT`         | `INTEGER` |
| `DOUBLE`    | `double precision` | `DOUBLE`      | `FLOAT`          | `REAL`    |
| `BOOLEAN`   | `boolean`          | `BOOLEAN`     | `BIT`            | `INTEGER` |
| `BYTES`     | `bytea`            | `LONGBLOB`    | `VARBINARY(MAX)` | `BLOB`    |
| `TIMESTAMP` | `timestamptz`      | `DATETIME(6)` | `DATETIME2(6)`   | `TEXT`    |
| `MAP`       | `jsonb`            | `JSON`        | `NVARCHAR(MAX)`  | `TEXT`    |
| `ARRAY`     | `jsonb`            | `JSON`        | `NVARCHAR(MAX)`  | `TEXT`    |

The standard columns:

| Column         | Kind        | PostgreSQL                 | Constraint          |
| -------------- | ----------- | -------------------------- | ------------------- |
| `uid`          | `STRING`    | `varchar(36)`              | Primary key         |
| `name`         | `STRING`    | `varchar(255) COLLATE "C"` | Unique index        |
| `parent`       | `STRING`    | `varchar(255)`             | Index, with parents |
| `display_name` | `STRING`    | `varchar(255)`             | `NOT NULL`          |
| `labels`       | `MAP`       | `jsonb`                    | `NOT NULL`          |
| `revision`     | `INTEGER`   | `bigint`                   | `NOT NULL`          |
| `create_time`  | `TIMESTAMP` | `timestamptz`              | `NOT NULL`          |
| `update_time`  | `TIMESTAMP` | `timestamptz`              | `NOT NULL`          |
| `delete_time`  | `TIMESTAMP` | `timestamptz`              | Nullable            |
| `purge_time`   | `TIMESTAMP` | `timestamptz`              | Nullable            |

Give `name` a binary collation, so that its order and uniqueness match the bytes of the name: `COLLATE "C"` on
PostgreSQL, `utf8mb4_bin` on MariaDB and `Latin1_General_100_BIN2` on SQL Server.
