# Rotate a key

Rotate a key to start using new material, on a schedule or at once. What older versions encrypted or signed stays
readable until you disable those versions.

## Before you begin

- Have a key, such as `keys/payments`. For more information, refer to [Encrypt data](encrypt-data.md).
- Hold the relation `admin` on the key.

## Rotate on a schedule

Give the key a rotation period in seconds when you create it. Here, 90 days:

```java
data.createResource(CreateResourceRequest.newBuilder().setSubject("user:anne").setParent("organizations/acme")
    .setResourceId("payments").setResource(Resource.newBuilder().setType("pyrite.dev/Key")
        .putFields("purpose", Value.newBuilder().setStringValue("ENCRYPTION").build())
        .putFields("algorithm", Value.newBuilder().setStringValue("AES256_GCM").build())
        .putFields("rotation_period", Value.newBuilder().setIntegerValue(7_776_000).build()))
    .build()).await();
```

The key rotates at its first operation after `next_rotation_time`. A period of `0` turns scheduled rotation off.

## Rotate now

1. Create a version under the key, with no id and no fields:

   ```java
   Resource version = data.createResource(CreateResourceRequest.newBuilder().setSubject("user:anne")
       .setParent("keys/payments").setResource(Resource.newBuilder().setType("pyrite.dev/KeyVersion")).build())
       .await();
   ```

2. Check the new primary. `version.getName()` is the next version, such as `keyVersions/payments-2`. New encryptions
   name it in `key_version`.

## Retire an old version

1. Re-encrypt what the old version encrypted. Each ciphertext's `key_version` tells you which version made it.

2. Disable the old version:

   ```java
   data.updateResource(UpdateResourceRequest.newBuilder().setSubject("user:anne")
       .setResource(Resource.newBuilder().setName("keyVersions/payments-1")
           .putFields("state", Value.newBuilder().setStringValue("DISABLED").build()))
       .setUpdateMask(FieldMask.newBuilder().addPaths("state")).setRevision(1).build()).await();
   ```

   A disabled version no longer decrypts or verifies. Set `state` to `ENABLED` to undo it.

3. Delete the version when nothing needs it.

   WARNING: A version that is purged cannot decrypt anything again. Data that only it encrypted is lost.

The primary version cannot be disabled or deleted. Both fail with `FAILED_PRECONDITION`.

## Related resources

- [Keys and versions](../concepts/keys-and-versions.md)
- [Pyrite's types](../reference/pyrite-types.md)
