# Rotate a key

Rotate a key to start using new material, on a schedule or at once. What older versions encrypted or signed stays
readable until you disable those versions.

## Before you begin

- Have a key, such as `keys/payments`. For more information, refer to [Encrypt data](encrypt-data.md).
- Hold the relation `admin` on the key.

## Rotate on a schedule

Give the key a rotation period when you create it. Here, 90 days:

```java
cryptography.createKey(CreateKeyRequest.newBuilder().setSubject("user:anne").setKeyId("payments")
    .setParent("organizations/acme").setAlgorithm(Algorithm.AES256_GCM)
    .setRotationPeriod(Duration.newBuilder().setSeconds(7_776_000)).build()).await();
```

The key rotates at its first operation after `next_rotation_time`.

To change the period of a key that exists, name the key's revision that you read:

```java
Key key = cryptography.getKey(GetKeyRequest.newBuilder().setSubject("user:anne").setName("keys/payments").build())
    .await();
cryptography.updateKey(UpdateKeyRequest.newBuilder().setSubject("user:anne").setName("keys/payments")
    .setRotationPeriod(Duration.newBuilder().setSeconds(2_592_000)).setRevision(key.getRevision()).build()).await();
```

A period of zero turns scheduled rotation off.

## Rotate now

1. Rotate the key:

   ```java
   Key rotated = cryptography.rotateKey(RotateKeyRequest.newBuilder().setSubject("user:anne")
       .setName("keys/payments").build()).await();
   ```

2. Check the new primary. `rotated.getPrimaryVersion()` is the next version, such as `keyVersions/payments-2`. New
   encryptions name it in `key_version`.

## Retire an old version

1. Re-encrypt what the old version encrypted. Each ciphertext's `key_version` tells you which version made it.

2. List the versions, to read the old version's revision:

   ```java
   KeyVersion old = cryptography.listKeyVersions(ListKeyVersionsRequest.newBuilder().setSubject("user:anne")
       .setKey("keys/payments").build()).await().getVersions(0);
   ```

   The versions come oldest first.

3. Disable the old version:

   ```java
   cryptography.updateKeyVersion(UpdateKeyVersionRequest.newBuilder().setSubject("user:anne")
       .setName(old.getName()).setState(KeyVersion.State.DISABLED).setRevision(old.getRevision()).build()).await();
   ```

   A disabled version no longer decrypts or verifies. Set the state to `ENABLED` to undo it.

4. Destroy the version when nothing needs it:

   ```java
   cryptography.deleteKeyVersion(DeleteKeyVersionRequest.newBuilder().setSubject("user:anne")
       .setName(old.getName()).build()).await();
   ```

   WARNING: A destroyed version cannot decrypt anything again. Data that only it encrypted is lost.

The primary version cannot be disabled or destroyed. Both fail with `FAILED_PRECONDITION`.

## Related resources

- [Keys and versions](../concepts/keys-and-versions.md)
- [Pyrite's types](../reference/pyrite-types.md)
