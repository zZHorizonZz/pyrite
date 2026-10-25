# Troubleshooting Pyrite

Each entry is headed by the message of the failed call. Its code block shows the status and the message. For every
status, refer to [Status codes](reference/status-codes.md).

## No declaration is written; write one with pyrite.schema.v1.Schema...

```txt
FAILED_PRECONDITION: No declaration is written; write one with pyrite.schema.v1.Schema.WriteDeclaration.
```

**Cause:** the services have no declaration to read. Every call reads the latest one.

**Fix:** write the declaration with `WriteDeclaration` when the application starts, before other calls. For more
information, refer to [Get started](get-started.md#5-write-the-declaration).

**Verify:** `ReadDeclaration` with an empty `id` returns the declaration.

## The subject user:bob has no owner on projects/ledger.

```txt
PERMISSION_DENIED: The subject user:bob has no owner on projects/ledger.
```

**Cause:** the subject does not hold the relation that the call checks. The message names the relation: the type's
`create` permission on the parent, its `grant` permission on the resource, or the relation that a key or an account
call checks.

**Fix:** grant the relation, or a relation that leads to it, such as `admin` on the organization. Check that the
request names the subject that you expect.

**Verify:** `Check` with that principal, the resource and the relation returns `allowed: true`.

## The resource projects/ledger is not known.

```txt
NOT_FOUND: The resource projects/ledger is not known.
```

**Cause:** your application never made the resource known with `CreateObject`, or it forgot it with `DeleteObject`.
`Check`, the grant methods and `CreateObject` under it need a known resource.

**Fix:** call `CreateObject` when your application creates the resource. For resources that existed before, make
each one known once, parents first. For more information, refer to [Resources](concepts/resources.md).

**Verify:** `Check` on the name returns `allowed` instead of failing.

## The subject 'anne' is not a principal of the form type:id, such...

```txt
INVALID_ARGUMENT: The subject 'anne' is not a principal of the form type:id, such as user:anne.
```

**Cause:** the request's `subject` is an id or a resource name, not a principal.

**Fix:** name the principal: `user:anne`, not `anne` or `accounts/anne`.

**Verify:** the call no longer fails with `INVALID_ARGUMENT`.

## The key keys/payments is at revision 2, not 1.

```txt
ABORTED: The key keys/payments is at revision 2, not 1.
```

**Cause:** the key or the key version changed after the caller read it, such as by a rotation.

**Fix:** read it again with `GetKey` or `ListKeyVersions`, apply the change to what you read, and send its new
`revision`.

**Verify:** the change returns the key or the version at the next revision.

## The relation editor of the type acme.dev/Project has directly...

```txt
INVALID_ARGUMENT: The relation editor of the type acme.dev/Project has directly related types exactly when its rewrite has this.
```

**Cause:** the relation's rewrite has `this` but no direct types in `metadata`, or direct types but no `this`.

**Fix:** add the relation to `metadata` with `directly_related_user_types`, or remove them when nothing is granted
the relation directly.

**Verify:** `WriteDeclaration` returns an id.

## The declaration has keys, whose material a root key encrypts, but...

```txt
FAILED_PRECONDITION: The declaration has keys, whose material a root key encrypts, but the options of the service have none.
```

**Cause:** the declaration has `pyrite.dev/Key`, and the options have no root key.

**Fix:** give the options a root key with `withRoot`, and give every service the same options. For more information,
refer to [Serve the services](how-to/serve-the-services.md#give-pyrite-a-root-key).

**Verify:** `WriteDeclaration` returns an id.

## The declaration has accounts, which need the type pyrite.dev/Key...

```txt
FAILED_PRECONDITION: The declaration has accounts, which need the type pyrite.dev/Key and the options of authentication.
```

**Cause:** the declaration has `pyrite.dev/Account`, but no `pyrite.dev/Key`, or the options have no authentication
options.

**Fix:** declare `pyrite.dev/Key`, and give the options `withAuthentication`. For more information, refer to
[Sign in with a password](how-to/sign-in-with-a-password.md#set-up-accounts).

**Verify:** `WriteDeclaration` returns an id.

## The flow sign-in starts from an identifier, but the declaration...

```txt
INVALID_ARGUMENT: The flow sign-in starts from an identifier, but the declaration has no identifier kinds.
```

**Cause:** the flow's input is `IDENTIFIER` or `SUBJECT_IDENTIFIER`, and the declaration has no `identifier_kinds`.
Pyrite has no kinds of its own.

**Fix:** declare the kinds that people sign in with, such as `email`. For the fields of a kind, refer to
[Declaration](reference/declaration.md#identifierkind).

**Verify:** `WriteDeclaration` returns an id.

## The flow verify sends a code to an identifier, but no identifier...

```txt
INVALID_ARGUMENT: The flow verify sends a code to an identifier, but no identifier kind of the declaration receives codes.
```

**Cause:** the flow has a stage with `identifier: true`, and no declared kind has `receives_codes: true`.

**Fix:** set `receives_codes: true` on each kind that your application can send a code to, such as `email` or
`phone`.

**Verify:** `WriteDeclaration` returns an id.

## The value anne@acme is of no identifier kind of the declaration.

```txt
INVALID_ARGUMENT: The value anne@acme is of no identifier kind of the declaration.
```

**Cause:** `AddIdentifier` names no `kind`, and the value meets the `match` of no declared kind.

**Fix:** check the value that the person typed. If it is valid, widen the `match` of its kind, or declare a kind for
it. Pass the value as the person typed it: the kind's `normalize` trims and lowercases it.

**Verify:** `AddIdentifier` returns the identifier, with its `kind` and its normalized `value`.

## The key keys/sessions does not exist.

```txt
NOT_FOUND: The key keys/sessions does not exist.
```

**Cause:** a flow needs the MAC key or the encryption key that the authentication options name, and nobody created
it.

**Fix:** create both keys with `CreateKey` after the first declaration, with the names that the options give.

**Verify:** a flow that issues a session returns a `SessionToken`.

## The resource organizations/acme has resources under it; forget...

```txt
FAILED_PRECONDITION: The resource organizations/acme has resources under it; forget them first.
```

**Cause:** `DeleteObject` does not forget a resource while known resources are under it.

**Fix:** forget the resources under it first, from the lowest level up. For more information, refer to
[Purge deleted resources](how-to/purge-deleted-resources.md).

**Verify:** `DeleteObject` succeeds, and `Check` on the name fails with `NOT_FOUND`.

## The flow flows/9f1c... took 5 answers; start another.

```txt
RESOURCE_EXHAUSTED: The flow flows/9f1c2a64-7d1e-4f0a-9a43-1f6c0e2b8d55 took 5 answers; start another.
```

**Cause:** the flow took `maxAnswers` answers, and takes no more.

**Fix:** start a new flow. To slow guessing down, keep the default of `5`, and limit how often a caller starts
flows.

**Verify:** `StartFlow` returns a flow with challenges.

## The session has ended.

```txt
UNAUTHENTICATED: The session has ended.
```

**Cause:** the session passed its `expire_time` or its idle expiry, or a flow or `EndSession` ended it, or its
account was deleted.

**Fix:** sign in again. To keep active sessions longer, raise `idleLifetime` in the options.

**Verify:** `Authenticate` with the new token returns the principal.

## Internal error

```txt
INTERNAL: Internal error
```

**Cause:** a failure that Pyrite did not expect, such as a lost connection to the database. The status gives no
details.

**Fix:** read the log of the service, under the logger `dev.horizon.pyrite.impl.UnaryHandler`, at the level `ERROR`.

**Verify:** the call succeeds once the cause in the log is fixed.
