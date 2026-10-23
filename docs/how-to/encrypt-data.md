# Encrypt data

Encrypt data with an encryption key, such as card numbers or API tokens that your application stores. Pyrite keeps
the key's material, and your application keeps only the ciphertext.

## Before you begin

- Declare `pyrite.dev/Key` with the relations `admin`, `viewer`, `encrypter`, `decrypter`, `signer` and `verifier`,
  and the parents that keys live under. For more information, refer to [Pyrite's types](../reference/pyrite-types.md).
- Give the services a root key with `withRoot`. For more information, refer to
  [Serve the services](serve-the-services.md#give-pyrite-a-root-key).
- Add the cryptography service to your server with `CryptographyService.create`.

## Encrypt and decrypt

1. Create an encryption key under its parent:

   ```java
   data.createResource(CreateResourceRequest.newBuilder().setSubject("user:anne").setParent("organizations/acme")
       .setResourceId("payments").setResource(Resource.newBuilder().setType("pyrite.dev/Key")
           .putFields("purpose", Value.newBuilder().setStringValue("ENCRYPTION").build())
           .putFields("algorithm", Value.newBuilder().setStringValue("AES256_GCM").build()))
       .build()).await();
   ```

2. Encrypt, with associated data that ties the ciphertext to where you store it, such as the order's id:

   ```java
   EncryptResponse encrypted = cryptography.encrypt(EncryptRequest.newBuilder().setSubject("user:anne")
       .setKey("keys/payments").setPlaintext(ByteString.copyFromUtf8("4111 1111 1111 1111"))
       .setAssociatedData(ByteString.copyFromUtf8("orders/1042")).build()).await();
   ```

3. Store `encrypted.getCiphertext()`.

4. Decrypt with the same key and the same associated data:

   ```java
   DecryptResponse decrypted = cryptography.decrypt(DecryptRequest.newBuilder().setSubject("user:anne")
       .setKey("keys/payments").setCiphertext(encrypted.getCiphertext())
       .setAssociatedData(ByteString.copyFromUtf8("orders/1042")).build()).await();
   System.out.println(decrypted.getPlaintext().toStringUtf8());
   ```

   ```txt
   4111 1111 1111 1111
   ```

## Check the result

`encrypted.getKeyVersion()` names the version that encrypted, such as `keyVersions/payments-1`. Decryption with other
associated data fails with `INVALID_ARGUMENT`, so a ciphertext copied to another row does not decrypt there.

## Let a service decrypt

Grant a principal only the relation that it needs. A service account that decrypts, but never encrypts:

```java
access.createGrant(CreateGrantRequest.newBuilder().setSubject("user:anne").setGrant(Grant.newBuilder()
    .setObject("keys/payments").setRelation("decrypter").setGrantee("user:billing")).build()).await();
```

## Related resources

- [Keys and versions](../concepts/keys-and-versions.md)
- [Rotate a key](rotate-a-key.md)
