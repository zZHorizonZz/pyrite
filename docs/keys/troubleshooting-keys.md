# Troubleshooting keys

The errors of the cryptography service, the most frequent first; each block shows the status that the call ends with,
and its message.

## The subject ... has no ... on keys/...

```text
PERMISSION_DENIED: The subject user:bob has no decrypter on keys/payments.
```

**Cause:** The subject does not hold the relation that the call checks on the key: `viewer` to read it, `admin` to
change it or its versions, and `encrypter`, `decrypter`, `signer` or `verifier` to use it. `CreateKey` checks the
type's `create` permission on the parent, and names the parent, such as `has no admin on organizations/acme`.

**Fix:** Grant the relation, or a relation that leads to it in the declaration of `pyrite.dev/Key`, such as `admin`.
Check that the request names the subject that you expect.

**Verify:** `Check` with the subject, the key and the relation returns `allowed: true`, and the call succeeds.

## The key ... does not exist

```text
NOT_FOUND: The key keys/payments does not exist.
```

**Cause:** No key has the name. Nobody created it, or `DeleteObject` forgot it, which deletes the key with its
versions.

**Fix:** Create the key with `CreateKey`, under a known parent. A key that the options of authentication name, such
as `keys/sessions`, is created by your application after the first declaration.

**Verify:** `GetKey` returns the key.

## The key ... encrypts; it does not sign

```text
FAILED_PRECONDITION: The key keys/payments encrypts; it does not sign.
```

**Cause:** `Sign` or `Verify` with a key of the purpose `ENCRYPTION`. `Encrypt` or `Decrypt` with a key of the purpose
`MAC` or `SIGNING` fails the same way with `The key keys/oauth signs; it does not encrypt.`

**Fix:** Use a key whose algorithm is for the operation: `AES256_GCM` to encrypt, `HMAC_SHA256`, `RS256`, `ES256` or
`EDDSA` to sign. The algorithm of a key never changes, so create another key.

**Verify:** The call succeeds with the other key.

## The ciphertext was not made by this key with that associated data, or was changed

```text
INVALID_ARGUMENT: The ciphertext was not made by this key with that associated data, or was changed.
```

**Cause:** `Decrypt` names other associated data than the encryption did, or the ciphertext changed after the
encryption, such as by a cut, a byte added, or a text encoding on the way to your table.

**Fix:** Decrypt with the associated data of the encryption, such as the same order name. Store the ciphertext as
bytes, or encode it with Base64.

**Verify:** `Decrypt` returns the plaintext.

## The ciphertext was not made by the key ...

```text
INVALID_ARGUMENT: The ciphertext was not made by the key keys/payments.
```

**Cause:** The ciphertext names a version of another key, or bytes that no key made.

**Fix:** Decrypt with the key that `key_version` of the encryption names: `keyVersions/billing-1` belongs to
`keys/billing`.

**Verify:** `Decrypt` returns the plaintext.

## The version ... that encrypted is disabled

```text
FAILED_PRECONDITION: The version keyVersions/payments-1 that encrypted is disabled.
```

**Cause:** The version that the ciphertext names was disabled with `UpdateKeyVersion`.

**Fix:** Enable the version again with the state `ENABLED`, decrypt, and encrypt the plaintext again with the primary
version.

**Verify:** `Decrypt` returns the plaintext.

## The version ... that encrypted is destroyed

```text
FAILED_PRECONDITION: The version keyVersions/payments-1 that encrypted is destroyed.
```

**Cause:** The version that the ciphertext names was destroyed with `DeleteKeyVersion`.

**Fix:** None: the material of the version is gone, and so is the plaintext. Before you destroy a version, encrypt
again what it encrypted. See [Rotate a key](rotate-a-key.md#retire-an-old-version).

**Verify:** `ListKeyVersions` lists no version that a stored ciphertext names.

## The key ... is at revision ..., not ...

```text
ABORTED: The key keys/payments is at revision 2, not 1.
```

**Cause:** `UpdateKey` names a revision of the key that is not its current one: the key changed after the caller read
it, such as by a rotation. `UpdateKeyVersion` fails the same way with
`The key version keyVersions/payments-1 is at revision 2, not 1.`

**Fix:** Read the key again with `GetKey`, or the version with `ListKeyVersions`, and send the change with its new
`revision`.

**Verify:** The change returns the key or the version at the next revision.

## The version ... is the primary version of its key; rotate the key first

```text
FAILED_PRECONDITION: The version keyVersions/payments-2 is the primary version of its key; rotate the key first.
```

**Cause:** `UpdateKeyVersion` disables, or `DeleteKeyVersion` destroys, the version that encrypts and signs.

**Fix:** Rotate the key with `RotateKey`, and then disable or destroy the old version.

**Verify:** The call succeeds, and `GetKey` names another `primary_version`.

## The key version ... does not exist

```text
NOT_FOUND: The key version keyVersions/payments-3 does not exist.
```

**Cause:** `UpdateKeyVersion` or `DeleteKeyVersion` names a version that the key does not have, or one that was
destroyed.

**Fix:** List the versions with `ListKeyVersions`, and name one of them.

**Verify:** The call returns the version.

## The name ... is not a key's, keys/{key}

```text
INVALID_ARGUMENT: The name payments is not a key's, keys/{key}.
```

**Cause:** The request names the id of a key, or a resource of another type, where it needs the name of a key.

**Fix:** Name the key as `keys/payments`. `CreateKey` takes the id alone, `payments`, in `key_id`.

**Verify:** The call succeeds.

## The request names no algorithm

```text
INVALID_ARGUMENT: The request names no algorithm, one of [AES256_GCM, HMAC_SHA256, RS256, ES256, EDDSA].
```

**Cause:** `CreateKey` leaves `algorithm` unset.

**Fix:** Set the algorithm, which decides the purpose of the key. See
[About keys and versions](about-keys-and-versions.md#purposes-and-algorithms).

**Verify:** `CreateKey` returns the key, with its purpose.

## The resource keys/... is known already

```text
ALREADY_EXISTS: The resource keys/payments is known already.
```

**Cause:** `CreateKey` names the id of a key that exists.

**Fix:** Use the key that exists, or create the key with another id.

**Verify:** `CreateKey` returns the key with its first version.

## The rotation period is shorter than a minute

```text
INVALID_ARGUMENT: The rotation period is shorter than a minute; it is zero for a key that does not rotate on its own.
```

**Cause:** `CreateKey` or `UpdateKey` names a `rotation_period` out of its range: a period is a whole number of
seconds, from a minute to 50 years, or zero. The other periods fail the same way, with
`The rotation period is negative.`, `The rotation period is not a whole number of seconds.`, or
`The rotation period is longer than 50 years.`

**Fix:** Name whole seconds from 60 to 3,155,760,000 in `seconds`, with `nanos` at zero, or zero for a key that rotates
only on request.

**Verify:** The call returns the key, with its `next_rotation_time` set for a period other than zero.

## The key ... changed while it rotated; rotate it again

```text
ABORTED: The key keys/payments changed while it rotated; rotate it again.
```

**Cause:** Another change of the key came before `RotateKey` made its new version the primary one, such as another
rotation at the same time, or an `UpdateKey`. The rotation deleted its new version again.

**Fix:** Read the key with `GetKey`. If another rotation gave it a new `primary_version`, there is nothing to do;
otherwise, rotate it again.

**Verify:** `GetKey` names a new `primary_version`, and `ListKeyVersions` lists it.

## A version is ENABLED or DISABLED

```text
INVALID_ARGUMENT: A version is ENABLED or DISABLED.
```

**Cause:** `UpdateKeyVersion` leaves `state` unset.

**Fix:** Set the state to `ENABLED` or `DISABLED`.

**Verify:** `UpdateKeyVersion` returns the version in that state.

## The request names neither data nor claims to sign

```text
INVALID_ARGUMENT: The request names neither data nor claims to sign.
```

**Cause:** `Sign` sets neither `data` nor `claims`. `Verify` that sets neither `detached` nor `token` fails with
`The request names neither a signature nor a token to verify.`

**Fix:** Set `data` to sign bytes, or `claims` to sign a JWT. To verify, set `detached` with the data and the
signature, or `token`.

**Verify:** `Sign` returns a signature or a token.

## The declaration has no keys; declare the type pyrite.dev/Key

```text
FAILED_PRECONDITION: The declaration has no keys; declare the type pyrite.dev/Key.
```

**Cause:** A call of the cryptography service, while the latest declaration has no `pyrite.dev/Key`.

**Fix:** Declare `pyrite.dev/Key`, give Pyrite a root key, and write the declaration again. See
[Pyrite's types](../declarations/pyrite-types.md).

**Verify:** `CreateKey` returns the key.

## The token is not of the type ...

A token that does not verify is not an error: `Verify` succeeds, and its response holds a rejection with the reason
and a message.

```text
rejected { reason: TYPE message: "The token is not of the type at+jwt." }
```

**Cause:** The token is not what the request expects. The most frequent reasons are `TYPE`, for a token signed with
another `type` than the request expects (an empty type on either side is `JWT`), and `EXPIRED`, for a token without
`exp` or after `exp` and the leeway.

**Fix:** Sign and verify with the same `type`, and sign every token with `exp`. For every reason, see
[Status codes](../deployment/status-codes.md#rejections-of-verify).

**Verify:** The response holds `verified`, with the claims of the token.

## Related

- [About keys and versions](about-keys-and-versions.md)
- [Troubleshooting deployment](../deployment/troubleshooting-deployment.md), for a missing root key
- [Status codes](../deployment/status-codes.md)
