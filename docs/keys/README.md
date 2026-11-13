# Keys

A key is a resource of the type `pyrite.dev/Key`, which the cryptography service, `pyrite.crypto.v1.Cryptography`,
creates and uses. Your application encrypts, decrypts, signs and verifies with a key by its name, such as
`keys/payments`, and never holds its material. Each call checks a relation of its subject on the key.

## Features

**Purposes.** The algorithm of a key decides what it does: `AES256_GCM` encrypts, `HMAC_SHA256` signs with a MAC, and
`RS256`, `ES256` and `EDDSA` sign with a key pair. See [About keys and versions](about-keys-and-versions.md).

**Encryption.** A ciphertext names the version that made it, and decrypts only with its key and the associated data
that it was encrypted with. See [Encrypt data](encrypt-data.md).

**Signatures and tokens.** Sign data as it is, or claims as a compact JWT, and verify them. A token that does not
verify is a rejection with its reason, not an error. Each version of a signing key publishes its public key as a JWK.
See [Sign and verify tokens](sign-and-verify-tokens.md).

**Rotation.** A key rotates on a schedule or on request, and its old versions still decrypt and verify until you
disable or destroy them. See [Rotate a key](rotate-a-key.md).

**A root key.** A Tink `Aead` outside the database seals the material of every version. See
[Serve the services](../deployment/serve-the-services.md#give-pyrite-a-root-key).

The errors of keys are in [Troubleshooting keys](troubleshooting-keys.md).

## Related

- [Pyrite's types](../declarations/pyrite-types.md)
- [Accounts](../accounts/README.md), whose refresh tokens, access tokens and factor secrets use three keys
- [Status codes](../deployment/status-codes.md)
