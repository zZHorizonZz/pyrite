# Providers

A provider holds the keys of a keyring and their versions. It decides where versions live and where their operations
run. The keyring, its keys and every call stay the same, whatever the provider.

## Where versions live

| Provider | Versions live | Operations run | Artifact |
| --- | --- | --- | --- |
| `DatabaseProvider` | in your PostgreSQL, MariaDB, SQL Server or SQLite tables, encrypted under the vault key | in the process | `pyrite` |
| `MemoryProvider` | in memory | in the process | `pyrite` |
| `KeysetFileProvider` | in a Tink keyset file, encrypted under a root key | in the process | `pyrite` |
| `CloudKmsProvider` | in Google Cloud KMS | in Cloud KMS | `pyrite-gcpkms` |
| `AwsKmsProvider` | in AWS KMS | in AWS KMS | `pyrite-awskms` |

- **Memory** keeps nothing after the process ends. Use it in tests.
- **A keyset file** belongs to one process. Two processes that write to one file lose each other's versions.
- **A cloud KMS** never lets a private key leave it. Your process sends it the data to encrypt or sign.

The keyring does the rest in the process for every provider. It writes the format of ciphertexts, fingerprints and
JWTs, and it chooses the version. It rotates on schedule, publishes the public keys and verifies signatures.

## The root key

The database and keyset file providers protect versions with two keys:

- **The root key** is any Tink `Aead` that you give the provider: a local keyset, such as one that
  `tinkey create-keyset --key-template AES256_GCM` writes, or a key in a cloud KMS through Tink's KMS clients.
- **The vault key** is an AES-256 key that the provider makes the first time it opens on a database. It is stored
  encrypted under the root key, and it encrypts the secret material of every version.

The provider decrypts the vault key once, when it opens. The root key is not used again until it rotates, so a root
key in a cloud KMS costs one call to the KMS for each start.

To rotate the root key of the database provider:

1. Add a key to the root keyset, and make it primary.
2. Call `rewrap()` on the provider. It encrypts the vault key again under the new primary key. The versions do not
   change.
3. Remove the old key from the root keyset.

A provider whose root keyset can no longer decrypt the vault key fails to open with an `IllegalStateException`.

## Capabilities

A provider declares what it can do in its `Capabilities`. The keyring checks them before it asks the provider.

| Capability | Means |
| --- | --- |
| `ENCRYPTION` | the provider holds encryption keys |
| `MAC` | the provider holds MAC keys |
| `SIGNING` | the provider holds signing keys, of the algorithms it lists |
| `EXPORTED_VERSIONS` | the provider makes versions whose private half it gives away |
| `UPLOADED_VERSIONS` | the provider adds versions for public keys that their holders made |

A call that needs a capability the provider lacks fails with `UnimplementedException`. The detail names the
provider and what it does not do. For example, `export()` fails on a cloud KMS.

## One provider for each keyring

A keyring has exactly one provider. To keep TOTP seeds in your database and session keys in Cloud KMS, create two
keyrings. Pyrite never moves a key from one provider to another.

## What a provider is not

- A provider does not choose when a version is made or which version is primary. The keyring decides.
- A provider does not format ciphertexts, fingerprints or tokens.
- A provider does not verify signatures. The keyring verifies them against the public keys.
