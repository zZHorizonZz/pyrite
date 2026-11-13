# Troubleshooting deployment

The errors of serving Pyrite, its options and its first declaration, the most frequent first; each block shows the
status that the call ends with and its message, or the exception that your code gets.

## No declaration is written; write one with pyrite.schema.v1.Schema.WriteDeclaration

```text
FAILED_PRECONDITION: No declaration is written; write one with pyrite.schema.v1.Schema.WriteDeclaration.
```

**Cause:** A call of the access, cryptography or authentication service before any declaration is in the table
`pyrite_declaration`. Every call reads the latest declaration.

**Fix:** Write the declaration with `WriteDeclaration` when the application starts, before other calls. See
[Get started](../get-started.md#5-write-the-declaration).

**Verify:** `ReadDeclaration` with an empty `id` returns the declaration, and the call succeeds.

## The declaration has keys, whose material a root key seals, but the options name no root key

```text
FAILED_PRECONDITION: The declaration has keys, whose material a root key seals, but the options name no root key.
```

**Cause:** The declaration has `pyrite.dev/Key`, and the key options of this Pyrite have no root key.
`WriteDeclaration` fails so, and so does every call of a Pyrite without a root key after another Pyrite on the same
database wrote such a declaration.

**Fix:** Give the key options a root key with `setRoot`, and give every Pyrite on the database the same options. See
[Serve the services](serve-the-services.md#give-pyrite-a-root-key).

**Verify:** `WriteDeclaration` returns an id, and the calls of every Pyrite succeed.

## The declaration has accounts, which need the type pyrite.dev/Key and the options of authentication

```text
FAILED_PRECONDITION: The declaration has accounts, which need the type pyrite.dev/Key and the options of authentication; these lack macKey, issuer.
```

**Cause:** `WriteDeclaration` of a declaration with `pyrite.dev/Account`, while the options of authentication lack what
the message names: `macKey`, `encryptionKey`, `accessTokenKey`, `issuer` or `relyingPartyOptions`, whose id and name are
both needed. A declaration without `pyrite.dev/Key` fails with `...; it has no keys.` at the end.

**Fix:** Declare `pyrite.dev/Key`, and set what the message names in the options of authentication. See
[Options](options.md#authenticationoptions).

**Verify:** `WriteDeclaration` returns an id, and `missing()` of the options is empty.

## The key keys/sessions does not exist

```text
NOT_FOUND: The key keys/sessions does not exist.
```

**Cause:** `macKey`, `encryptionKey` or `accessTokenKey` of the options of authentication names a key that nobody
created. An exchange that sends a code or issues a session, and `RefreshSession`, need the key of `macKey`; issuing a
session and `Authenticate` need the key of `accessTokenKey`; and the enrolment of a TOTP factor needs the key of
`encryptionKey`.

**Fix:** After the first declaration, create the three keys with `CreateKey`, with the ids of the names in the
options: `sessions` with `HMAC_SHA256`, `factors` with `AES256_GCM`, and `access` with `ES256`.

**Verify:** An exchange that issues a session returns its tokens.

## The key keys/sessions encrypts; it does not sign

```text
FAILED_PRECONDITION: The key keys/sessions encrypts; it does not sign.
```

**Cause:** `macKey` names a key of the purpose `ENCRYPTION`. When `encryptionKey` names a key that signs, the calls
fail with `The key keys/factors signs; it does not encrypt.`

**Fix:** Name a key of `HMAC_SHA256` in `macKey`, a key of `AES256_GCM` in `encryptionKey`, and a key of `ES256`,
`EDDSA` or `RS256` in `accessTokenKey`. The algorithm of a key
never changes, so create a key with the right algorithm and name it in the options of every Pyrite.

**Verify:** An exchange that issues a session returns its tokens.

## The name sessions is not a key's, keys/{key}

```text
INVALID_ARGUMENT: The name sessions is not a key's, keys/{key}.
```

**Cause:** `macKey` or `encryptionKey` holds the id of a key, not its name.

**Fix:** Set the name of the key, such as `setMacKey("keys/sessions")`.

**Verify:** An exchange that issues a session returns its tokens.

## Internal error

```text
INTERNAL: Internal error
```

**Cause:** A failure that Pyrite did not expect, such as tables that the migrations did not create, a lost
connection to the database, or an OpenFGA service that does not answer. The status gives no details.

**Fix:** Read the log of the service, under the logger `dev.horizon.pyrite.PyriteException`, at the level `ERROR`,
with the message `A Pyrite call failed` and the failure. Fix what the failure names, such as running the migrations.
See [Create the tables](create-the-tables.md).

**Verify:** The call succeeds.

## The root key does not open the version ...

```text
A Pyrite call failed
java.lang.IllegalStateException: The root key does not open the version keyVersions/payments-1
```

**Cause:** The log of a call that ended with `INTERNAL: Internal error`. The root key of this Pyrite is not the one
that sealed the version, such as a new root key that a test generates at each start, or a Pyrite with another root
key on the same database.

**Fix:** Give every Pyrite on the database the root key that sealed the versions. A version whose root key is lost
does not open again.

**Verify:** `Encrypt` and `Decrypt` with the key succeed.

## The ... is positive

```text
java.lang.IllegalArgumentException: The session lifetime is positive.
```

**Cause:** A setter of the options got a value that no Pyrite runs with. The other messages are
`The idle lifetime is positive.`, `The exchange lifetime is positive.`, `The code lifetime is positive.`,
`An exchange takes at least one answer.`, `The clock skew is not negative.`, `The default page size is at least 1.`,
`The largest page is at least 1.`, `A read takes at least 1 tuple.`, and `The memory is at least 1.`,
`The iterations is at least 1.` and `The parallelism is at least 1.` for Argon2.

**Fix:** Set a value within the limits of the option. See [Options](options.md).

**Verify:** `Pyrite.pyrite` starts with the options.

## No declaration is written

```text
NOT_FOUND: No declaration is written.
```

**Cause:** `ReadDeclaration` with an empty `id` before any declaration was written. With an `id`, a declaration that
was never written fails with `The declaration 3f2a... does not exist.`

**Fix:** Write the declaration with `WriteDeclaration`, and read it with the `id` that it returns, or with an empty
`id` for the latest.

**Verify:** `ReadDeclaration` returns the declaration, its id and its `create_time`.

## Related

- [Serve the services](serve-the-services.md)
- [Options](options.md)
- [Status codes](status-codes.md)
- [Troubleshooting keys](../keys/troubleshooting-keys.md)
