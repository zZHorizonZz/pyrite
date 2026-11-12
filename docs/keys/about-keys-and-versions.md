# About keys and versions

A key is a resource of the type `pyrite.dev/Key`, such as `keys/payments`. The cryptography service creates it, and
encrypts, decrypts, signs and verifies with it. A key has versions, which hold its material. One version is the
primary: it encrypts and signs. Every enabled version decrypts and verifies what it made.

```java
cryptography.createKey(CreateKeyRequest.newBuilder().setSubject("user:anne").setKeyId("payments")
    .setParent("organizations/acme").setAlgorithm(Algorithm.AES256_GCM).build()).await();
EncryptResponse first = cryptography.encrypt(EncryptRequest.newBuilder().setSubject("user:anne")
    .setKey("keys/payments").setPlaintext(ByteString.copyFromUtf8("4111 1111 1111 1111")).build()).await();
cryptography.rotateKey(RotateKeyRequest.newBuilder().setSubject("user:anne").setName("keys/payments").build())
    .await();
EncryptResponse second = cryptography.encrypt(EncryptRequest.newBuilder().setSubject("user:anne")
    .setKey("keys/payments").setPlaintext(ByteString.copyFromUtf8("4111 1111 1111 1111")).build()).await();
```

`first.getKeyVersion()` is `keyVersions/payments-1`, and `second.getKeyVersion()` is `keyVersions/payments-2`. Both
ciphertexts decrypt with `keys/payments`, because the first version is still enabled.

## Purposes and algorithms

The algorithm of a key decides its purpose, and the purpose decides what the key does. You choose the algorithm when
you create the key with `CreateKey`, and it never changes.

| Algorithm     | Purpose      | Material of a version      | `alg` of its JWTs | Operations       |
| ------------- | ------------ | -------------------------- | ----------------- | ---------------- |
| `AES256_GCM`  | `ENCRYPTION` | A 256-bit AES key          | None              | Encrypt, Decrypt |
| `HMAC_SHA256` | `MAC`        | A 256-bit HMAC-SHA256 key  | `HS256`           | Sign, Verify     |
| `RS256`       | `SIGNING`    | A 2048-bit RSA key pair    | `RS256`           | Sign, Verify     |
| `ES256`       | `SIGNING`    | An ECDSA key pair on P-256 | `ES256`           | Sign, Verify     |
| `EDDSA`       | `SIGNING`    | An Ed25519 key pair        | `EdDSA`           | Sign, Verify     |

A MAC key and a signing key sign data as it is, or sign claims as a compact JWT. Each version of a signing key has its
public key as a JWK, which `ListKeyVersions` returns in `public_key`, so that anyone can verify its tokens without
Pyrite. A version of a MAC key has no public key.

## Who can use a key

Each call checks a relation of its subject on the key. You declare the relations in the declaration of
`pyrite.dev/Key`, as for any type, so you decide who holds them:

| Call                                                             | Relation                                     |
| ---------------------------------------------------------------- | -------------------------------------------- |
| `CreateKey`                                                      | The type's `create` permission on the parent |
| `GetKey`, `ListKeyVersions`                                      | `viewer`                                     |
| `UpdateKey`, `RotateKey`, `UpdateKeyVersion`, `DeleteKeyVersion` | `admin`                                      |
| `Encrypt`                                                        | `encrypter`                                  |
| `Decrypt`                                                        | `decrypter`                                  |
| `Sign`                                                           | `signer`                                     |
| `Verify`                                                         | `verifier`                                   |

The declaration of `pyrite.dev/Key` has the relations `admin`, `viewer`, `encrypter`, `decrypter`, `signer` and
`verifier`, and leaves `plural` empty: Pyrite names keys `keys/<id>`. Keys sit under the parents that the declaration
names, such as an organization. The subject that creates a key gets the type's `creator` relation on it. See
[Pyrite's types](../declarations/pyrite-types.md).

## Versions

A version is named after the id of its key and its number: `keyVersions/payments-1`, `keyVersions/payments-2`. It is
not a declared type: the cryptography service keeps it with its key, and a call on a version checks the relations of
its key. `CreateKey` creates the first version with the key.

A rotation creates the next version and makes it the primary. Older versions stay, so they still decrypt and verify
what they made. Rotations at the same time each make a version of their own, numbered without a gap. A key rotates in
two ways:

- **On a schedule.** A key with a `rotation_period` has a `next_rotation_time`. Its first encryption or signature after
  that time rotates it, once, and moves `next_rotation_time` on by the period. A decryption or a verification does not
  rotate a key. `UpdateKey` changes the period, and the next rotation is then a period after the change.
- **On request.** `RotateKey` rotates the key at once, and its next scheduled rotation is a period after it.

The database decides when a rotation is due, on its own clock, so several Pyrites on one database rotate a key once.

A version is `ENABLED` or `DISABLED`. `UpdateKeyVersion` changes the state. A disabled version neither decrypts nor
verifies, and enabling it again restores both. `DeleteKeyVersion` destroys a version: it deletes its row with its
material, and nothing that the version encrypted decrypts again. The primary version is neither disabled nor
destroyed; rotate the key first. See [Rotate a key](rotate-a-key.md).

## Ciphertexts and tokens

A ciphertext starts with the id of the version that encrypted it, such as `payments-1`, so that `Decrypt` finds the
version. The 12-byte nonce of AES-GCM and the sealed plaintext with its tag follow. AES-GCM authenticates the name of
the key together with the associated data of the request, so a ciphertext decrypts only with the same key and the same
associated data. A ciphertext with a byte changed, cut short or extended does not decrypt.

A JWT carries the id of the version that signed it in the `kid` of its header, such as `oauth-1`. `Verify` checks the
token with that version, so a token of another key, or with a `kid` that the key does not have, does not verify.

## The root key

Pyrite stores the material of each version in the table `pyrite_key_version`, sealed by a root key. The root key is a
Tink `Aead` that you give in the key options with `setRoot`. Keep it outside the database, such as in a cloud KMS. The
root key seals the material of a version for the name of that version, so material copied to the row of another
version does not open.

A declaration that has `pyrite.dev/Key` is refused without a root key. Pyrite keeps the material of the 256 versions
that it used last in memory, and asks the root key again for any other. See
[Serve the services](../deployment/serve-the-services.md#give-pyrite-a-root-key).

## What a key is not

- A key is not exported. No call returns its material, only the public key of a signing key's version.
- A key is not tied to one service. Every Pyrite on the same database and root key uses the same keys.
- A version is not destroyed by a rotation. Only `DeleteKeyVersion` destroys it.

## Related

- [Encrypt data](encrypt-data.md)
- [Sign and verify tokens](sign-and-verify-tokens.md)
- [Rotate a key](rotate-a-key.md)
- [Troubleshooting keys](troubleshooting-keys.md)
