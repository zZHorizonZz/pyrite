# Pyrite's types

Pyrite has six types of its own. You declare two of them, `pyrite.dev/Key` and `pyrite.dev/Account`, with their
relations, permissions and parents. Pyrite adds the other four beneath them. For how keys and accounts work, refer to
[Keys and versions](../concepts/keys-and-versions.md) and [Accounts and flows](../concepts/accounts-and-flows.md).

| Type                    | Plural        | Table                | Parent               | Declared by |
| ----------------------- | ------------- | -------------------- | -------------------- | ----------- |
| `pyrite.dev/Key`        | `keys`        | `pyrite_key`         | Your choice          | You         |
| `pyrite.dev/KeyVersion` | `keyVersions` | `pyrite_key_version` | `pyrite.dev/Key`     | Pyrite      |
| `pyrite.dev/Account`    | `accounts`    | `pyrite_account`     | None                 | You         |
| `pyrite.dev/Identifier` | `identifiers` | `pyrite_identifier`  | `pyrite.dev/Account` | Pyrite      |
| `pyrite.dev/Factor`     | `factors`     | `pyrite_factor`      | `pyrite.dev/Account` | Pyrite      |
| `pyrite.dev/Session`    | `sessions`    | `pyrite_session`     | `pyrite.dev/Account` | Pyrite      |

The types that Pyrite adds have the relations `viewer` and `admin`, read from the parent. Their permissions are
`create: "admin"`, `read: "viewer"`, and `admin` for the rest. Their retention is their parent's.

In each table of fields below, "Pyrite" means that only Pyrite sets the field, "Creation" means that the caller sets
it once at creation, and "Caller" means that the caller sets it at creation and at updates. The standard fields are
on every type, and are not repeated.

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
  permissions { create: "admin" read: "viewer" update: "admin" delete: "admin" grant: "admin" }
  grantable: ["admin", "viewer", "encrypter", "decrypter", "signer", "verifier"]
  creator: "admin"
  retention { seconds: 2592000 }
}
```

| Field                | Kind        | Set by   | Description                                                           |
| -------------------- | ----------- | -------- | --------------------------------------------------------------------- |
| `purpose`            | `STRING`    | Creation | `ENCRYPTION`, `MAC` or `SIGNING`                                      |
| `algorithm`          | `STRING`    | Creation | An algorithm of the purpose; refer to the table below                 |
| `rotation_period`    | `INTEGER`   | Caller   | Seconds between rotations; `0` for none. Default: `0`                 |
| `next_rotation_time` | `TIMESTAMP` | Pyrite   | When the key rotates next; unset without a rotation period            |
| `primary_version`    | `STRING`    | Pyrite   | The version that new operations use, such as `keyVersions/payments-1` |

| Algorithm     | Purpose      | Operation                      |
| ------------- | ------------ | ------------------------------ |
| `AES256_GCM`  | `ENCRYPTION` | AES-256 in GCM mode            |
| `HMAC_SHA256` | `MAC`        | HMAC with SHA-256; JWT `HS256` |
| `RS256`       | `SIGNING`    | RSASSA-PKCS1-v1_5 with SHA-256 |
| `ES256`       | `SIGNING`    | ECDSA on P-256 with SHA-256    |
| `EDDSA`       | `SIGNING`    | EdDSA; JWT `EdDSA`             |

## pyrite.dev/KeyVersion

A version is named `keyVersions/<key id>-<number>`. Create one under a key, with no id and no fields, to rotate the
key.

| Field             | Kind      | Set by | Description                                                         |
| ----------------- | --------- | ------ | ------------------------------------------------------------------- |
| `number`          | `INTEGER` | Pyrite | `1` for the first version, one more for each rotation               |
| `state`           | `STRING`  | Caller | `ENABLED` or `DISABLED`. Default: `ENABLED`                         |
| `algorithm`       | `STRING`  | Pyrite | The key's algorithm                                                 |
| `public_key`      | `MAP`     | Pyrite | The public key as a JWK, for a signing key                          |
| `sealed`          | `BYTES`   | Pyrite | The material, encrypted by the root key. Never returned             |
| `public_key_info` | `BYTES`   | Pyrite | The X.509 SubjectPublicKeyInfo of a signing version. Never returned |

## pyrite.dev/Account

The declaration must have the relations `owner`, `admin` and `viewer`. `owner` must take `user` directly, without a
condition or wildcard. Pyrite grants each account `owner` on itself when it creates the account. The singular name is
`user`, so `accounts/anne` is the principal `user:anne`.

| Field  | Kind     | Set by   | Description          |
| ------ | -------- | -------- | -------------------- |
| `kind` | `STRING` | Creation | `HUMAN` or `SERVICE` |

## pyrite.dev/Identifier

| Field      | Kind      | Set by   | Description                                                   |
| ---------- | --------- | -------- | ------------------------------------------------------------- |
| `kind`     | `STRING`  | Creation | `EMAIL`, `PHONE` or `USERNAME`                                |
| `value`    | `STRING`  | Creation | The address, number or name. See the formats below            |
| `verified` | `BOOLEAN` | Pyrite   | Whether a `MARK_VERIFIED` flow proved it. Default: `false`    |
| `primary`  | `BOOLEAN` | Caller   | Whether the account prefers this identifier. Default: `false` |

| Kind       | Format                            | Stored as                  |
| ---------- | --------------------------------- | -------------------------- |
| `EMAIL`    | `[^@\s]+@[^@\s]+\.[^@\s]+`        | Trimmed and lowercased     |
| `PHONE`    | `\+[1-9][0-9]{6,14}`, as in E.164 | Without spaces and hyphens |
| `USERNAME` | `[a-z0-9][a-z0-9._-]{2,63}`       | Trimmed and lowercased     |

## pyrite.dev/Factor

Factors are created only by enrolment flows. `CreateResource` fails with `FAILED_PRECONDITION`.

| Field           | Kind      | Set by | Description                                                       |
| --------------- | --------- | ------ | ----------------------------------------------------------------- |
| `kind`          | `STRING`  | Pyrite | `PASSWORD`, `TOTP`, `PASSKEY` or `RECOVERY_CODES`                 |
| `state`         | `STRING`  | Pyrite | `PENDING` until the flow activates it, then `ACTIVE`              |
| `secret`        | `BYTES`   | Pyrite | The factor's secret, hashed, encrypted or as MACs. Never returned |
| `credential_id` | `STRING`  | Pyrite | The WebAuthn credential id of a passkey                           |
| `counter`       | `INTEGER` | Pyrite | A passkey's signature counter, or the last TOTP time step used    |
| `user_verified` | `BOOLEAN` | Pyrite | Whether the passkey verified the user at enrolment                |

## pyrite.dev/Session

Sessions are issued only by flows. `CreateResource` fails with `FAILED_PRECONDITION`. `DeleteResource` ends one.

| Field              | Kind        | Set by | Description                                                         |
| ------------------ | ----------- | ------ | ------------------------------------------------------------------- |
| `token_mac`        | `BYTES`     | Pyrite | The MAC of the token's secret. Never returned                       |
| `expire_time`      | `TIMESTAMP` | Pyrite | `sessionLifetime` after the session was issued                      |
| `idle_expire_time` | `TIMESTAMP` | Pyrite | `idleLifetime` after the last `Authenticate`, at most `expire_time` |
