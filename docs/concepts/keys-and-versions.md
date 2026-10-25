# Keys and versions

A key is a resource of the type `pyrite.dev/Key`, such as `keys/payments`. The cryptography service creates it, and
encrypts, decrypts, signs and verifies with it. A key has versions, which hold its material. One version is the
primary, and new operations use it.

## Purposes and algorithms

A key's algorithm decides its purpose, and the purpose decides what the key does. You choose the algorithm when you
create the key with `CreateKey`, and it never changes.

| Purpose      | Algorithms                | Operations       |
| ------------ | ------------------------- | ---------------- |
| `ENCRYPTION` | `AES256_GCM`              | Encrypt, Decrypt |
| `MAC`        | `HMAC_SHA256`             | Sign, Verify     |
| `SIGNING`    | `RS256`, `ES256`, `EDDSA` | Sign, Verify     |

A MAC key and a signing key sign data as it is, or sign claims as a compact JWT. Each version of a signing key has a
public key as a JWK, which `ListKeyVersions` returns in `public_key`, so that anyone can verify its tokens without
Pyrite.

## Who can use a key

Each call checks a relation on the key. You declare the relations in the declaration of `pyrite.dev/Key`, as for any
type, so you decide who holds them:

| Call                                                             | Relation                                      |
| ---------------------------------------------------------------- | --------------------------------------------- |
| `CreateKey`                                                      | The type's `create` permission, on the parent |
| `GetKey`, `ListKeyVersions`                                      | `viewer`                                      |
| `UpdateKey`, `RotateKey`, `UpdateKeyVersion`, `DeleteKeyVersion` | `admin`                                       |
| `Encrypt`                                                        | `encrypter`                                   |
| `Decrypt`                                                        | `decrypter`                                   |
| `Sign`                                                           | `signer`                                      |
| `Verify`                                                         | `verifier`                                    |

The declaration of `pyrite.dev/Key` must have the relations `admin`, `viewer`, `encrypter`, `decrypter`, `signer` and
`verifier`. Keys sit under a parent that you declare, such as an organization. The subject that creates a key gets the
type's `creator` relation on it.

## Versions

A version is named after its key and its number: `keyVersions/payments-1`, `keyVersions/payments-2`. It is not a
declared type: the cryptography service keeps it with its key, and checks the key's relations. `CreateKey` creates the
first version with the key.

A rotation creates the next version and makes it the primary. Older versions stay, so they still decrypt and verify
what they made. A key rotates in two ways:

- **On a schedule.** A key with a `rotation_period` rotates at the first operation after `next_rotation_time`.
- **On request.** `RotateKey` rotates it at once.

A version is `ENABLED` or `DISABLED`. `UpdateKeyVersion` changes the state, and a disabled version no longer decrypts
or verifies. `DeleteKeyVersion` destroys a version. The primary version cannot be disabled or destroyed; rotate the
key first. For the steps, refer to [Rotate a key](../how-to/rotate-a-key.md).

## The root key

Pyrite stores each version's material in its table, encrypted by a root key. The root key is a Tink `Aead` that you
give in the options with `withRoot`. Keep it outside the database, such as in a cloud KMS.

A declaration that has `pyrite.dev/Key` fails without a root key. Pyrite keeps the material that it decrypted in
memory, so a version is decrypted once per declaration and service.

## What a key is not

- A key is not exported. No call returns its material, only a signing key's public key.
- A key is not tied to one service. Every service on the same database and root key uses the same keys.
- A version is not destroyed by a rotation. Only `DeleteKeyVersion` destroys it.
