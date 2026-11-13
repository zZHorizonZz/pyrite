# Encrypt data

Encrypt data that your application stores, such as card numbers or API tokens, with a key whose material stays in
Pyrite.

## Before you begin

- Declare `pyrite.dev/Key` with the relations `admin`, `viewer`, `encrypter`, `decrypter`, `signer` and `verifier`,
  and the parents that keys sit under. See [Pyrite's types](../declarations/pyrite-types.md).
- Give Pyrite a root key in its key options. See
  [Serve the services](../deployment/serve-the-services.md#give-pyrite-a-root-key).
- Make the parent of the key known, such as `organizations/acme`, with `CreateObject`. See
  [About resources](../access/about-resources.md).
- Have a client of the cryptography service, `CryptographyGrpcClient.create(client)`. See
  [Serve the services](../deployment/serve-the-services.md).

## Create an encryption key

1. Create the key under its parent, with the algorithm `AES256_GCM`, which gives it the purpose `ENCRYPTION`:

   ```java
   Key key = cryptography.createKey(CreateKeyRequest.newBuilder().setSubject("user:anne").setKeyId("payments")
       .setParent("organizations/acme").setAlgorithm(Algorithm.AES256_GCM).build()).await();
   ```

   Anne needs the `create` permission of `pyrite.dev/Key` on the organization, and gets the type's `creator`
   relation on the key. `key.getPrimaryVersion()` is `keyVersions/payments-1`.

## Encrypt and decrypt

1. Encrypt, with associated data that ties the ciphertext to where you store it, such as the name of the order:

   ```java
   EncryptResponse encrypted = cryptography.encrypt(EncryptRequest.newBuilder().setSubject("user:anne")
       .setKey("keys/payments").setPlaintext(ByteString.copyFromUtf8("4111 1111 1111 1111"))
       .setAssociatedData(ByteString.copyFromUtf8("orders/1042")).build()).await();
   ```

   The subject needs `encrypter` on the key.

2. Store `encrypted.getCiphertext()` in the row of the order. Pyrite does not store it.

3. Decrypt with the same key and the same associated data:

   ```java
   DecryptResponse decrypted = cryptography.decrypt(DecryptRequest.newBuilder().setSubject("user:anne")
       .setKey("keys/payments").setCiphertext(encrypted.getCiphertext())
       .setAssociatedData(ByteString.copyFromUtf8("orders/1042")).build()).await();
   System.out.println(decrypted.getPlaintext().toStringUtf8());
   ```

   ```text
   4111 1111 1111 1111
   ```

   The subject needs `decrypter` on the key. `Decrypt` uses the version that the ciphertext names, so a ciphertext of
   an older version decrypts after a rotation.

## Verify

`encrypted.getKeyVersion()` and `decrypted.getKeyVersion()` name the same version, `keyVersions/payments-1`. Two
encryptions of the same plaintext give different ciphertexts. A decryption with other associated data fails with
`INVALID_ARGUMENT`, so a ciphertext copied to the row of another order does not decrypt there. See
[Troubleshooting keys](troubleshooting-keys.md).

## Let a service decrypt

Grant a principal only the relation that it needs. A service account that decrypts, but never encrypts, gets
`decrypter` alone:

```java
access.createGrant(CreateGrantRequest.newBuilder().setSubject("user:anne").setGrant(Grant.newBuilder()
    .setObject("keys/payments").setRelation("decrypter").setGrantee("user:billing")).build()).await();
```

The relation must be in the `grantable` of `pyrite.dev/Key`, and Anne needs the type's `grant` permission on the key.

## Related

- [About keys and versions](about-keys-and-versions.md)
- [Rotate a key](rotate-a-key.md)
- [Troubleshooting keys](troubleshooting-keys.md)
