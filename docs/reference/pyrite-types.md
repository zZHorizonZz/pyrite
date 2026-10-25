# Pyrite's types

Pyrite has two types of its own, `pyrite.dev/Key` and `pyrite.dev/Account`. You declare them with their relations,
permissions and parents, and leave `plural` empty. Pyrite keeps their resources, and the records that each holds: a
key's versions, and an account's identifiers, factors and sessions. Those records are not declared types; each call
on them checks a relation on their key or their account. For how keys and accounts work, refer to
[Keys and versions](../concepts/keys-and-versions.md) and [Accounts and flows](../concepts/accounts-and-flows.md).

| Type or record       | Name                    | Table                | Under       | Service          |
| -------------------- | ----------------------- | -------------------- | ----------- | ---------------- |
| `pyrite.dev/Key`     | `keys/<id>`             | `pyrite_key`         | Your choice | `Cryptography`   |
| Key version          | `keyVersions/<key>-<n>` | `pyrite_key_version` | Its key     | `Cryptography`   |
| `pyrite.dev/Account` | `accounts/<id>`         | `pyrite_account`     | None        | `Authentication` |
| Identifier           | `identifiers/<id>`      | `pyrite_identifier`  | Its account | `Authentication` |
| Factor               | `factors/<id>`          | `pyrite_factor`      | Its account | `Authentication` |
| Session              | `sessions/<id>`         | `pyrite_session`     | Its account | `Authentication` |

Keys and accounts are known resources, as your own are: `Check`, `CreateGrant` and the other methods of the access
service work on them. Pyrite generates the ids of identifiers, factors and sessions.

## pyrite.dev/Key

The declaration must have the relations `admin`, `viewer`, `encrypter`, `decrypter`, `signer` and `verifier`. For
example, a key under an organization:

```textproto
types {
  name: "pyrite.dev/Key"
  parents: "acme.dev/Organization"
  definition {
    type: "key"
    relations {
      key: "admin"
      value {
        union {
          child { this {} }
          child { tuple_to_userset { tupleset { relation: "parent" } computed_userset { relation: "admin" } } }
        }
      }
    }
    relations {
      key: "viewer"
      value { union { child { this {} } child { computed_userset { relation: "admin" } } } }
    }
    relations { key: "encrypter" value { union { child { this {} } child { computed_userset { relation: "admin" } } } } }
    relations { key: "decrypter" value { union { child { this {} } child { computed_userset { relation: "admin" } } } } }
    relations { key: "signer" value { union { child { this {} } child { computed_userset { relation: "admin" } } } } }
    relations { key: "verifier" value { union { child { this {} } child { computed_userset { relation: "signer" } } } } }
    metadata {
      relations { key: "admin" value { directly_related_user_types { type: "user" } } }
      relations { key: "viewer" value { directly_related_user_types { type: "user" } } }
      relations { key: "encrypter" value { directly_related_user_types { type: "user" } } }
      relations { key: "decrypter" value { directly_related_user_types { type: "user" } } }
      relations { key: "signer" value { directly_related_user_types { type: "user" } } }
      relations { key: "verifier" value { directly_related_user_types { type: "user" } } }
    }
  }
  permissions { create: "admin" grant: "admin" }
  grantable: ["admin", "viewer", "encrypter", "decrypter", "signer", "verifier"]
  creator: "admin"
}
```

The calls of the cryptography service check these relations:

| Call                                                             | Checks                                                    |
| ---------------------------------------------------------------- | --------------------------------------------------------- |
| `CreateKey`                                                      | The type's `create` permission, on the parent             |
| `GetKey`, `ListKeyVersions`                                      | `viewer` on the key                                       |
| `UpdateKey`, `RotateKey`, `UpdateKeyVersion`, `DeleteKeyVersion` | `admin` on the key                                        |
| `Encrypt`, `Decrypt`, `Sign`, `Verify`                           | `encrypter`, `decrypter`, `signer`, `verifier` on the key |

### Key

`CreateKey` takes the `key_id`, the `parent`, the `algorithm` and the `rotation_period`. It returns a `Key`:

| Field                | Type                        | Description                                                           |
| -------------------- | --------------------------- | --------------------------------------------------------------------- |
| `name`               | `string`                    | The name, such as `keys/payments`                                     |
| `parent`             | `string`                    | The resource it is under; empty for a key at the top                  |
| `purpose`            | `Key.Purpose`               | `ENCRYPTION`, `MAC` or `SIGNING`, which the algorithm decides         |
| `algorithm`          | `Algorithm`                 | The algorithm; refer to the table below                               |
| `rotation_period`    | `google.protobuf.Duration`  | How often the key rotates on its own; zero for never                  |
| `next_rotation_time` | `google.protobuf.Timestamp` | When the key rotates next; unset without a rotation period            |
| `primary_version`    | `string`                    | The version that new operations use, such as `keyVersions/payments-1` |
| `revision`           | `int64`                     | The revision that `UpdateKey` names                                   |
| `create_time`        | `google.protobuf.Timestamp` | When the key was created                                              |
| `update_time`        | `google.protobuf.Timestamp` | When the key last changed                                             |

`UpdateKey` changes only `rotation_period`.

| Algorithm     | Purpose      | Operation                      |
| ------------- | ------------ | ------------------------------ |
| `AES256_GCM`  | `ENCRYPTION` | AES-256 in GCM mode            |
| `HMAC_SHA256` | `MAC`        | HMAC with SHA-256; JWT `HS256` |
| `RS256`       | `SIGNING`    | RSASSA-PKCS1-v1_5 with SHA-256 |
| `ES256`       | `SIGNING`    | ECDSA on P-256 with SHA-256    |
| `EDDSA`       | `SIGNING`    | EdDSA; JWT `EdDSA`             |

### KeyVersion

A version is named `keyVersions/<key id>-<number>`. `CreateKey` creates the first, and each rotation the next.
`ListKeyVersions` returns them oldest first.

| Field         | Type                        | Description                                               |
| ------------- | --------------------------- | --------------------------------------------------------- |
| `name`        | `string`                    | The name, such as `keyVersions/payments-2`                |
| `number`      | `int64`                     | `1` for the first version, one more for each rotation     |
| `state`       | `KeyVersion.State`          | `ENABLED` or `DISABLED`, which `UpdateKeyVersion` changes |
| `algorithm`   | `Algorithm`                 | The key's algorithm                                       |
| `public_key`  | `google.protobuf.Struct`    | The public key as a JWK, for a version of a signing key   |
| `revision`    | `int64`                     | The revision that `UpdateKeyVersion` names                |
| `create_time` | `google.protobuf.Timestamp` | When the version was created                              |

No call returns a version's material. Pyrite keeps it encrypted by the root key.

## pyrite.dev/Account

The declaration must have the relations `owner`, `admin` and `viewer`. `owner` must take `user` directly, without a
condition or wildcard. Pyrite grants each account `owner` on itself when it creates the account. The singular name is
`user`, so `accounts/anne` is the principal `user:anne`. For example:

```textproto
types {
  name: "pyrite.dev/Account"
  definition {
    type: "user"
    relations { key: "owner" value { this {} } }
    relations {
      key: "admin"
      value { union { child { this {} } child { computed_userset { relation: "owner" } } } }
    }
    relations {
      key: "viewer"
      value { union { child { this {} } child { computed_userset { relation: "admin" } } } }
    }
    metadata {
      relations { key: "owner" value { directly_related_user_types { type: "user" } } }
      relations { key: "admin" value { directly_related_user_types { type: "user" } } }
      relations { key: "viewer" value { directly_related_user_types { type: "user" } } }
    }
  }
  permissions { grant: "admin" }
  grantable: ["admin", "viewer"]
}
```

The calls of the authentication service check these relations on the account:

| Call                                           | Checks               |
| ---------------------------------------------- | -------------------- |
| `CreateAccount`                                | Nothing; any subject |
| `GetAccount`, `ListIdentifiers`, `GetSession`  | `viewer`             |
| `DeleteAccount`, `AddIdentifier`, `EndSession` | `admin`              |

### Account

| Field         | Type                        | Description                                  |
| ------------- | --------------------------- | -------------------------------------------- |
| `name`        | `string`                    | The name, such as `accounts/anne`            |
| `kind`        | `Account.Kind`              | `HUMAN` or `SERVICE`, set at `CreateAccount` |
| `create_time` | `google.protobuf.Timestamp` | When the account was created                 |

### Identifier

`AddIdentifier` takes the `account`, the `value`, the `kind` and `primary`, and adds the identifier, unverified. A
flow that completes with `MARK_VERIFIED` verifies it.

| Field      | Type     | Description                                                            |
| ---------- | -------- | ---------------------------------------------------------------------- |
| `name`     | `string` | The name, such as `identifiers/0b0d...`                                |
| `account`  | `string` | The account, such as `accounts/anne`                                   |
| `kind`     | `string` | The kind, as the declaration names it, such as `email`                 |
| `value`    | `string` | The value, as the kind's `normalize` gives it, such as `anne@acme.dev` |
| `verified` | `bool`   | Whether a `MARK_VERIFIED` flow proved it                               |
| `primary`  | `bool`   | Whether the account prefers this identifier of its kind                |

The kinds are the declaration's `identifier_kinds`. Pyrite has none of its own. An empty `kind` in `AddIdentifier`
takes the first declared kind whose `match` the value meets. `AddIdentifier` fails with `INVALID_ARGUMENT` for a kind
that the declaration does not have, for a value that does not meet the kind that the request names, and for a value
of no kind. For the fields of a kind, refer to [Declaration](declaration.md#identifierkind).

### Factor

Factors come only from enrolment flows, in the result's `factors`. A factor's secret is never returned.

| Field     | Type          | Description                                       |
| --------- | ------------- | ------------------------------------------------- |
| `name`    | `string`      | The name, such as `factors/0b0d...`               |
| `account` | `string`      | The account, such as `accounts/anne`              |
| `kind`    | `Factor.Kind` | `PASSWORD`, `TOTP`, `PASSKEY` or `RECOVERY_CODES` |

### Session

Sessions come only from flows, in the result's `sessions`. `EndSession` ends one, and `Purge` deletes it once it has
ended.

| Field         | Type                        | Description                                    |
| ------------- | --------------------------- | ---------------------------------------------- |
| `name`        | `string`                    | The name, such as `sessions/0b0d...`           |
| `account`     | `string`                    | The account, such as `accounts/anne`           |
| `expire_time` | `google.protobuf.Timestamp` | `sessionLifetime` after the session was issued |
