# Rotate a key

Rotate a key to encrypt and sign with new material, on a schedule or at once, and retire its old versions.

## Before you begin

- Have a key, such as `keys/payments`. See [Encrypt data](encrypt-data.md).
- Hold the relation `admin` on the key.

## Rotate on a schedule

1. Give the key a rotation period when you create it. Here, 90 days:

   ```java
   cryptography.createKey(CreateKeyRequest.newBuilder().setSubject("user:anne").setKeyId("payments")
       .setParent("organizations/acme").setAlgorithm(Algorithm.AES256_GCM)
       .setRotationPeriod(Duration.newBuilder().setSeconds(7_776_000)).build()).await();
   ```

   The key's `next_rotation_time` is 90 days after its creation. Its first encryption or signature after that time
   rotates it.

2. To change the period of a key that exists, read the key, and name the revision that you read:

   ```java
   Key key = cryptography.getKey(GetKeyRequest.newBuilder().setSubject("user:anne").setName("keys/payments")
       .build()).await();
   cryptography.updateKey(UpdateKeyRequest.newBuilder().setSubject("user:anne").setName("keys/payments")
       .setRotationPeriod(Duration.newBuilder().setSeconds(2_592_000)).setRevision(key.getRevision()).build())
       .await();
   ```

   The next rotation is a period after the change. A period of zero turns scheduled rotation off and clears
   `next_rotation_time`. A key that changed since you read it fails with `ABORTED`; read it again and repeat.

## Rotate now

1. Rotate the key:

   ```java
   Key rotated = cryptography.rotateKey(RotateKeyRequest.newBuilder().setSubject("user:anne")
       .setName("keys/payments").build()).await();
   ```

2. Read `rotated.getPrimaryVersion()`. It is the next version, such as `keyVersions/payments-2`. New encryptions and
   signatures name it in `key_version`.

3. For a signing key, publish the public key of the new version. See
   [Sign and verify tokens](sign-and-verify-tokens.md#publish-the-public-key).

## Retire an old version

1. Re-encrypt what the old version encrypted: decrypt each ciphertext whose `key_version` is the old version, and
   encrypt it again. The new ciphertext names the primary version.

2. List the versions, oldest first, to read the old version's revision:

   ```java
   KeyVersion old = cryptography.listKeyVersions(ListKeyVersionsRequest.newBuilder().setSubject("user:anne")
       .setKey("keys/payments").build()).await().getVersions(0);
   ```

3. Disable the old version:

   ```java
   cryptography.updateKeyVersion(UpdateKeyVersionRequest.newBuilder().setSubject("user:anne")
       .setName(old.getName()).setState(KeyVersion.State.DISABLED).setRevision(old.getRevision()).build())
       .await();
   ```

   A disabled version neither decrypts nor verifies. Set the state to `ENABLED` to undo it.

4. Destroy the version when nothing needs it:

   ```java
   cryptography.deleteKeyVersion(DeleteKeyVersionRequest.newBuilder().setSubject("user:anne")
       .setName(old.getName()).build()).await();
   ```

   WARNING: A destroyed version cannot decrypt anything again. Data that only it encrypted is lost.

## Verify

`GetKey` gives the key with the new `primary_version`, and, for a key with a period, a `next_rotation_time` a period
from the rotation. `ListKeyVersions` lists the old version as `DISABLED` after step 3, and without it after step 4. A
ciphertext of a disabled version fails to decrypt with `FAILED_PRECONDITION`, and so does one of a destroyed version.
Disabling or destroying the primary version fails with `FAILED_PRECONDITION` too. See
[Troubleshooting keys](troubleshooting-keys.md).

## Related

- [About keys and versions](about-keys-and-versions.md)
- [Encrypt data](encrypt-data.md)
- [Sign and verify tokens](sign-and-verify-tokens.md)
