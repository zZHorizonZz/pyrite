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

## The subject user:bob has no viewer on projects/ledger.

```txt
PERMISSION_DENIED: The subject user:bob has no viewer on projects/ledger.
```

**Cause:** the subject does not hold the relation that the method checks. The message names the relation, from the
type's `permissions`.

**Fix:** grant the relation, or a relation that leads to it, such as `member` on the organization. Check that the
request names the subject that you expect.

**Verify:** `Check` with that principal, the resource and the relation returns `allowed: true`.

## The subject 'anne' is not a principal of the form type:id, such...

```txt
INVALID_ARGUMENT: The subject 'anne' is not a principal of the form type:id, such as user:anne.
```

**Cause:** the request's `subject` is an id or a resource name, not a principal.

**Fix:** name the principal: `user:anne`, not `anne` or `accounts/anne`.

**Verify:** the call no longer fails with `INVALID_ARGUMENT`.

## The resource projects/ledger is at revision 2, not 1.

```txt
ABORTED: The resource projects/ledger is at revision 2, not 1.
```

**Cause:** the resource changed after the caller read it.

**Fix:** read the resource again, apply the change to what you read, and send its new `revision`.

**Verify:** the change returns the resource at the next revision.

## The type acme.dev/Project lacks the standard field purge_time of...

```txt
INVALID_ARGUMENT: The type acme.dev/Project lacks the standard field purge_time of the kind TIMESTAMP.
```

**Cause:** the type's `fields` leave out a standard field, or give it another kind.

**Fix:** list all ten standard fields with their kinds. For the list, refer to
[Declaration](reference/declaration.md#field).

**Verify:** `WriteDeclaration` returns an id.

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

## The resource keys/sessions does not exist.

```txt
NOT_FOUND: The resource keys/sessions does not exist.
```

**Cause:** a flow needs the MAC key or the encryption key that the authentication options name, and nobody created
it.

**Fix:** create both keys after the first declaration, with the names that the options give.

**Verify:** a flow that issues a session returns a `SessionToken`.

## The resource organizations/acme has resources beneath it that are...

```txt
FAILED_PRECONDITION: The resource organizations/acme has resources beneath it that are not deleted.
```

**Cause:** a resource cannot be deleted while resources beneath it are active.

**Fix:** delete the resources beneath it first, from the lowest level up.

**Verify:** `DeleteResource` returns the resource with a `delete_time`.

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

**Cause:** the session passed its `expire_time` or its idle expiry, or a flow ended it, or it was deleted.

**Fix:** sign in again. To keep active sessions longer, raise `idleLifetime` in the options.

**Verify:** `Authenticate` with the new token returns the principal.

## A query returns fewer resources than the page size

There is no error. A query checks the permission on each row of a page, and leaves out the rows that the subject may
not see. A page can therefore hold fewer resources than the page size, even none, while more pages follow.

**Fix:** read pages until `next_page_token` is empty. For more information, refer to
[Subjects and permissions](concepts/subjects-and-permissions.md#queries).

## Internal error

```txt
INTERNAL: Internal error
```

**Cause:** a failure that Pyrite did not expect, such as a lost connection to the database. The status gives no
details.

**Fix:** read the log of the service, under the logger `dev.horizon.pyrite.impl.UnaryHandler`, at the level `ERROR`.

**Verify:** the call succeeds once the cause in the log is fixed.
