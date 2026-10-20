# Providers

A provider stores the keys and versions of a keyring, and runs the cryptographic operations of each version. The
keyring does the rest: it chooses the version to use, rotates and expires versions, formats ciphertexts, MACs and
JWTs, and verifies signatures. Your code calls the keyring in the same way for every provider.

## Available providers

| Provider           | Stores versions in                                                       | Runs operations in | Artifact          |
| ------------------ | ------------------------------------------------------------------------ | ------------------ | ----------------- |
| `DatabaseProvider` | PostgreSQL, MariaDB, SQL Server or SQLite, encrypted under the vault key | your process       | `pyrite-database` |
| `MemoryProvider`   | memory, encrypted under a vault key that is never stored                 | your process       | `pyrite-memory`   |

Use `MemoryProvider` in tests. It loses every key when the process ends.

Providers for Tink keyset files, Google Cloud KMS and AWS KMS are planned. A KMS provider will keep private keys in
the KMS, so your process will send data to the KMS to sign.

## The database

`DatabaseProvider.open` takes a Cinder `Database` that you build, so you configure it as you need. The database must
have the protobuf extension, because Pyrite's tables are protobuf messages. Without it, `open` throws
`IllegalArgumentException`.

```java
Database database = Database.builder(pool, new PostgresDriver())
    .extension(new ProtobufExtension())
    .pageTokenKey(pageTokenKey)
    .build();
DatabaseProvider provider = DatabaseProvider.open(vertx, database, root).await(10, TimeUnit.SECONDS);
```

`Keyring.keys` returns page tokens that Cinder encrypts with the page token key, an AES key. If you do not set one,
each process creates a random key, and a page token works only in the process that returned it. If several processes
serve the same clients, give them all the same page token key
([Cinder, list pages](https://github.com/zZHorizonZz/cinder/blob/main/docs/how-to/read-rows.md#list-pages)).

## Root key and vault key

The database provider uses envelope encryption, as Cloud KMS describes it: a data key encrypts the data, and a key
encryption key encrypts the data key
([Cloud KMS, envelope encryption](https://docs.cloud.google.com/kms/docs/envelope-encryption)). The provider protects
versions with two keys:

- The root key is a Tink `Aead` that you pass to `DatabaseProvider.open`. It can be a local keyset, or a key in a
  cloud KMS through one of Tink's KMS clients.
- The vault key is an AES-256 key that the provider creates the first time it opens on a database. The provider
  stores it encrypted under the root key, and uses it to encrypt the secret material of every version.

The provider decrypts the vault key once, when it opens. If the root key is in a cloud KMS, this is the only call to
the KMS. The provider keeps the decrypted material of a version in memory for an hour after it was last used, for at
most 10,000 versions at a time.

If another process disables or destroys a version, this process can keep its material in memory until that hour has
passed. It does not use the material, because each operation reads the state of the version from the database first.

To rotate the root key:

1. Add a new key to the root keyset, and make it primary.
2. Call `rewrap()` on the provider. It encrypts the vault key again under the new root key. The versions do not
   change.
3. Remove the old key from the root keyset.

If the root keyset cannot decrypt the vault key, `DatabaseProvider.open` fails with `IllegalStateException`.

## What the vault key protects

The vault key protects the material of versions from anyone who can read the database, for example from a backup, a
replica or a SQL injection that reads data.

It does not protect against anyone who can write to the database. A writer can add a version with their own public
key, or enable a disabled version. The database provider stores its tables in your application's database, where a
writer can already change your application's data. To keep your keys separate from your application's data, open the
provider on a database of its own.

## Capabilities

Each provider lists what it supports in its `Capabilities`. The keyring checks them before it calls the provider.

| Capability          | Description                                              |
| ------------------- | -------------------------------------------------------- |
| `ENCRYPTION`        | stores encryption keys                                   |
| `MAC`               | stores MAC keys                                          |
| `SIGNING`           | stores signing keys of the listed algorithms             |
| `EXPORTED_VERSIONS` | creates versions and returns their private keys          |
| `UPLOADED_VERSIONS` | adds versions for public keys that their holders created |

A call that needs a missing capability fails with `UnimplementedException`. The detail names the provider and the
operation. For example, a KMS provider will not support `EXPORTED_VERSIONS`, because a KMS does not release private
keys.

## One provider for each keyring

A keyring uses exactly one provider. To store some keys in your database and others in Cloud KMS, create two keyrings.
Pyrite cannot move a key from one provider to another.
