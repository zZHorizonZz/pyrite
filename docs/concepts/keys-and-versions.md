# Keys and versions

A key is a resource of the type `pyrite.dev/Key`, such as `keys/payments`. The cryptography service encrypts, decrypts,
signs and verifies with it. A key has versions, which hold its material. One version is the primary, and new
operations use it.

## Purposes and algorithms

A key's purpose decides what it does. Its algorithm must be one of its purpose. You set both when you create the key,
and they never change.

| Purpose      | Algorithms                | Operations       |
| ------------ | ------------------------- | ---------------- |
| `ENCRYPTION` | `AES256_GCM`              | Encrypt, Decrypt |
| `MAC`        | `HMAC_SHA256`             | Sign, Verify     |
| `SIGNING`    | `RS256`, `ES256`, `EDDSA` | Sign, Verify     |

A MAC key and a signing key sign data as it is, or sign claims as a compact JWT. A signing key's versions publish
their public key as a JWK, in the field `public_key`, so that anyone can verify its tokens without Pyrite.

## Who can use a key

Each operation checks a relation on the key. You declare the relations in the declaration of `pyrite.dev/Key`, as
for any type, so you decide who holds them:

| Operation | Relation    |
| --------- | ----------- |
| Encrypt   | `encrypter` |
| Decrypt   | `decrypter` |
| Sign      | `signer`    |
| Verify    | `verifier`  |

The declaration of `pyrite.dev/Key` must have these four relations, and `admin` and `viewer`. Keys live under a
parent that you declare, such as an organization.

## Versions

A version is a resource of the type `pyrite.dev/KeyVersion`, named after its key and its number:
`keyVersions/payments-1`, `keyVersions/payments-2`. Pyrite creates the first version with the key.

A rotation creates the next version and makes it the primary. Older versions stay, so they still decrypt and verify
what they made. A key rotates in two ways:

- **On a schedule.** A key with a `rotation_period` in seconds rotates at the first operation after the period ends.
- **On request.** Creating a `pyrite.dev/KeyVersion` under the key rotates it at once.

A version is `ENABLED` or `DISABLED`. A disabled version no longer decrypts or verifies. The primary version cannot be
disabled or deleted; rotate the key first. For the steps, refer to [Rotate a key](../how-to/rotate-a-key.md).

## The root key

Pyrite stores each version's material in its table, encrypted by a root key. The root key is a Tink `Aead` that you
give in the options with `withRoot`. Keep it outside the database, such as in a cloud KMS.

A declaration that has `pyrite.dev/Key` fails without a root key. Pyrite keeps the material that it decrypted in
memory, so a version is decrypted once per declaration and service.

## What a key is not

- A key is not exported. No call returns its material, only a signing key's public key.
- A key is not tied to one service. Every service on the same database and root key uses the same keys.
- A version is not deleted by a rotation. Only a call to delete it removes it.
