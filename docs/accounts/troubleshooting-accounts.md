# Troubleshooting accounts

The errors of accounts, identifiers, flows and sessions, the most frequent first, each with the status and the
message that the client of the authentication service gets.

## The answer is not right

```text
PERMISSION_DENIED: The answer is not right.
```

**Cause:** An answer that the stage takes, but that proves nothing: a wrong password, TOTP code, recovery code or code
of an identifier, a code that expired or that went to another flow, or a passkey that Pyrite refuses. A flow of an
unknown or unverified identifier gives this for every answer, so that it looks the same as a flow of a real account.

**Fix:** Let the person try again at the same revision; the wrong answer leaves the flow where it was. For a code,
check that the person types the code of this flow before it expires, and that the address is verified for a
recovery. For a passkey, see [Add a passkey](add-a-passkey.md#what-pyrite-refuses).

**Verify:** The right answer gives the flow at its next revision, or done.

## The session has ended

```text
UNAUTHENTICATED: The session has ended.
```

**Cause:** `Authenticate` with the token of a session that passed its `expire_time` or its idle expiry, that
`EndSession` or a flow with `END_SESSIONS` ended, or whose account was deleted. An account deleted while the token is
authenticated gives `The account of the session is deleted.` with the same status.

**Fix:** Sign the person in again. To keep sessions in use longer, raise `idleLifetime` or `sessionLifetime` in the
options of authentication. See [Options](../deployment/options.md).

**Verify:** `Authenticate` with the new token gives the principal.

## The session token is not valid

```text
UNAUTHENTICATED: The session token is not valid.
```

**Cause:** Text that is not a session token, or a token whose secret was cut short or changed, such as one with the
secret of another session. A token whose id, the part before the dot, names no live session gives
`The session has ended.` instead.

**Fix:** Pass the `token` of the `SessionToken` of the flow as it is, the id of the session, a dot and the secret.
Do not pass the name of the session, such as `sessions/0b0d...`.

**Verify:** `Authenticate` gives the principal of the session.

## The flow ... is at revision ..., not ...

```text
ABORTED: The flow flows/9f1c2a64-7d1e-4f0a-9a43-1f6c0e2b8d55 is at revision 2, not 1.
```

**Cause:** An answer that names a revision that the flow moved past, such as the revision of a page that the person
submitted twice.

**Fix:** Answer with the `revision` of the flow of the last response. To retry an answer whose response was lost, send
it with the same `request_id`, which gives the first response again.

**Verify:** The answer gives the flow at its next revision.

## The flow ... took ... answers; start another

```text
RESOURCE_EXHAUSTED: The flow flows/9f1c2a64-7d1e-4f0a-9a43-1f6c0e2b8d55 took 5 answers; start another.
```

**Cause:** The flow took `maxAnswers` wrong answers, and refuses every answer from then on, the right one too.

**Fix:** Start a new flow. To slow guessing down, keep the default of 5, and limit how often a caller starts flows.

**Verify:** `StartFlow` gives a flow with challenges.

## The flow ... expired

```text
FAILED_PRECONDITION: The flow flows/9f1c2a64-7d1e-4f0a-9a43-1f6c0e2b8d55 expired.
```

**Cause:** An answer after `flowLifetime`, 15 minutes by default from the start of the flow, on the clock of the
database.

**Fix:** Start a new flow. To give people longer, raise `flowLifetime` in the options of authentication.

**Verify:** The answer to the new flow gives it at its next revision.

## The flow ... takes no ... now

```text
INVALID_ARGUMENT: The flow flows/9f1c2a64-7d1e-4f0a-9a43-1f6c0e2b8d55 takes no type.googleapis.com/pyrite.authentication.v1.PasswordAnswer now.
```

**Cause:** An answer of a message that no challenge of the current stage names, such as a password at a
phishing-resistant stage, a second password at a stage of two factors, or a code at a stage of factors. The answer
does not count against the flow.

**Fix:** Answer with a message that a challenge of the last response names in its `method`, packed with `Any.pack`.

**Verify:** The answer gives the flow at its next revision, or `PERMISSION_DENIED` when it is wrong.

## A password has 8 to 128 characters

```text
INVALID_ARGUMENT: A password has 8 to 128 characters, as NIST SP 800-63B requires.
```

**Cause:** A new password, in a flow that enrols one, with fewer than 8 or more than 128 characters. Pyrite counts
characters as Unicode code points.

**Fix:** Ask the person for a password of 8 to 128 characters, and answer the same flow again.

**Verify:** The flow is done, and its result names a factor of the kind `PASSWORD`.

## The declaration has accounts, which need the type pyrite.dev/Key

```text
FAILED_PRECONDITION: The declaration has accounts, which need the type pyrite.dev/Key and the options of authentication; these lack macKey, issuer.
```

**Cause:** `WriteDeclaration` of a declaration with `pyrite.dev/Account`, while the options of authentication lack
what the message names, or the declaration has no `pyrite.dev/Key`, which ends the message with `it has no keys.`

**Fix:** Declare `pyrite.dev/Key`, and set what the message names in the options of authentication: `macKey`,
`encryptionKey`, `issuer`, and the id and the name of the relying party. See
[Sign in with a password](sign-in-with-a-password.md#set-up-accounts).

**Verify:** `WriteDeclaration` gives an id.

## The key ... does not exist

```text
NOT_FOUND: The key keys/sessions does not exist.
```

**Cause:** A flow needs the MAC key or the encryption key that the options of authentication name, and nobody created
it. A key of the wrong algorithm fails with `FAILED_PRECONDITION` instead: `The key keys/sessions encrypts; it does
not sign.`, or `The key keys/factors signs; it does not encrypt.`

**Fix:** Create both keys with `CreateKey` after the first declaration, under the names that the options give: an
`HMAC_SHA256` key for `macKey`, and an `AES256_GCM` key for `encryptionKey`.

**Verify:** A flow that issues a session gives a `SessionToken`.

## The declaration has no accounts

```text
FAILED_PRECONDITION: The declaration has no accounts; declare the type pyrite.dev/Account.
```

**Cause:** A call of the authentication service while the latest declaration has no `pyrite.dev/Account`. `StartFlow`
and `ContinueFlow` give `The declaration has no accounts, so it has no flows.` with the same status.

**Fix:** Declare `pyrite.dev/Account`, with the identifier kinds and the flows that you use, and write the declaration.

**Verify:** `CreateAccount` gives the account.

## The flow ... starts from an identifier, but the declaration has no identifier kinds

```text
INVALID_ARGUMENT: The flow sign-in starts from an identifier, but the declaration has no identifier kinds.
```

**Cause:** `WriteDeclaration` of a flow whose input is `IDENTIFIER` or `SUBJECT_IDENTIFIER`, in a declaration without
`identifier_kinds`. Pyrite has no kinds of its own.

**Fix:** Declare the kinds that people sign in with, such as `email`. See
[Declaration reference](../declarations/declaration-reference.md).

**Verify:** `WriteDeclaration` gives an id.

## The flow ... sends a code to an identifier, but no identifier kind of the declaration receives codes

```text
INVALID_ARGUMENT: The flow verify sends a code to an identifier, but no identifier kind of the declaration receives codes.
```

**Cause:** `WriteDeclaration` of a flow with a stage of `identifier: true`, while no declared kind has
`receives_codes: true`.

**Fix:** Set `receives_codes: true` on each kind that your application can send a code to, such as `email` or
`phone`.

**Verify:** `WriteDeclaration` gives an id.

## The subject ... has no ... on accounts/...

```text
PERMISSION_DENIED: The subject user:bob has no admin on accounts/anne.
```

**Cause:** A call on an account by a subject without the relation that the call checks: `viewer` for `GetAccount`,
`ListIdentifiers` and `GetSession`, and `admin` for `AddIdentifier`, `EndSession` and `DeleteAccount`.

**Fix:** Call as the account, such as `user:anne` for `accounts/anne`, or grant the subject the relation. Check that
`pyrite.dev/Account` builds `admin` and `viewer` from `owner`.

**Verify:** The call gives the account, its identifiers or its session.

## The ... is another account's or this one's already

```text
ALREADY_EXISTS: The email anne@acme.dev is another account's or this one's already.
```

**Cause:** `AddIdentifier` of a value that an account has, after the kind normalizes it, such as `Anne@Acme.dev` when
an account has `anne@acme.dev`.

**Fix:** Tell the person that the address is taken, and offer a sign-in or a recovery. To move an address to another
account, delete the account that has it.

**Verify:** `ListIdentifiers` of the account lists the identifier.

## The value ... is of no identifier kind of the declaration

```text
INVALID_ARGUMENT: The value anne@acme is of no identifier kind of the declaration.
```

**Cause:** `AddIdentifier` that names no `kind`, of a value that meets the `match` of no declared kind.

**Fix:** Check the value that the person typed. If it is valid, widen the `match` of its kind, or declare a kind for
it. Pass the value as the person typed it: the kind's `normalize` trims it.

**Verify:** `AddIdentifier` gives the identifier, with its `kind` and its normalized `value`.

## The value ... is not of the identifier kind ...

```text
INVALID_ARGUMENT: The value anne@acme is not of the identifier kind email.
```

**Cause:** `AddIdentifier` or `StartFlow` that names a kind, with a value that does not meet its `match`, or whose
normalized value no longer does.

**Fix:** Ask the person for a value of the kind. To accept any kind, leave the kind out, and Pyrite takes the first
kind whose `match` the value meets.

**Verify:** The call gives the identifier, or the flow.

## The declaration has no identifier kind ...

```text
INVALID_ARGUMENT: The declaration has no identifier kind mail.
```

**Cause:** `AddIdentifier` or `StartFlow` that names a kind that the declaration does not have, such as a typo.

**Fix:** Name a kind of `identifier_kinds` of the declaration, such as `email`, or leave the kind out.

**Verify:** The call gives the identifier, or the flow.

## The flow ... starts from an identifier, and the request names none

```text
INVALID_ARGUMENT: The flow sign-in starts from an identifier, and the request names none.
```

**Cause:** `StartFlow` of a flow whose input is `IDENTIFIER` or `SUBJECT_IDENTIFIER`, with an `identifier` that is
empty or blank.

**Fix:** Pass the identifier that the person typed in `identifier`. Check the form before the call, so that an empty
field does not reach Pyrite.

**Verify:** `StartFlow` gives a flow with challenges.

## The account ... has no ...

```text
NOT_FOUND: The account accounts/anne has no email anne@home.example.
```

**Cause:** `StartFlow` of a flow whose input is `SUBJECT_IDENTIFIER`, with an identifier that is not of the subject's
account, or of no account.

**Fix:** Pass an identifier of the account, as `ListIdentifiers` gives them. To add an address, call `AddIdentifier`
first.

**Verify:** `StartFlow` gives a flow with a delivery to the address.

## The flow ... is done

```text
FAILED_PRECONDITION: The flow flows/9f1c2a64-7d1e-4f0a-9a43-1f6c0e2b8d55 is done.
```

**Cause:** An answer to a flow that met its last stage, with a `request_id` other than that of its last answer.

**Fix:** Take the result from the response of the last answer. To get that response again, send the last answer with
its `request_id`. For a new sign-in, start a new flow.

**Verify:** The retry gives the same flow, with its result.

## The resource accounts/... is known already

```text
ALREADY_EXISTS: The resource accounts/anne is known already.
```

**Cause:** `CreateAccount` with an `account_id` that an account has.

**Fix:** Pick another id. The id is the id of the principal, so it is unique among the users of your application.

**Verify:** `CreateAccount` gives the account.

## The id of accounts/... does not match ...

```text
INVALID_ARGUMENT: The id of accounts/Anne does not match [a-z]([a-z0-9-]{0,61}[a-z0-9])?.
```

**Cause:** `CreateAccount` with an `account_id` that has upper case letters, other characters than letters, digits and
hyphens, or more than 63 characters.

**Fix:** Give the account an id of lower case letters, digits and hyphens, such as a random one. Keep what the person
types, such as a name or an address, in an identifier.

**Verify:** `CreateAccount` gives the account.

## An account's kind is HUMAN or SERVICE

```text
INVALID_ARGUMENT: An account's kind is HUMAN or SERVICE.
```

**Cause:** `CreateAccount` without a `kind`.

**Fix:** Set the kind: `HUMAN` for a person, `SERVICE` for a service.

**Verify:** `CreateAccount` gives the account with its kind.

## The account ... does not exist

```text
NOT_FOUND: The account accounts/bob does not exist.
```

**Cause:** A call that names an account that nobody created, or that was deleted: `GetAccount`, `AddIdentifier`,
`ListIdentifiers`, `DeleteAccount`, or `StartFlow` of a flow whose input is `SUBJECT` or `SUBJECT_IDENTIFIER`, whose
subject has no account.

**Fix:** Create the account first, and call with the subject of the account, such as `user:bob` for `accounts/bob`.

**Verify:** The call gives the account, or the flow.

## The subject ... is not an account

```text
INVALID_ARGUMENT: The subject group:staff is not an account.
```

**Cause:** `StartFlow` of a flow whose input is `SUBJECT` or `SUBJECT_IDENTIFIER`, with a subject of another type
than `user`. A subject that is not of the form `type:id`, such as `anne` or `accounts/anne`, fails with
`The subject 'anne' is not a principal of the form type:id, such as user:anne.` instead.

**Fix:** Start the flow as the account, such as `user:anne`.

**Verify:** `StartFlow` gives a flow with challenges.

## The flow ... is not declared

```text
INVALID_ARGUMENT: The flow sign-up is not declared.
```

**Cause:** `StartFlow` of a `definition` that the latest declaration does not have, such as a typo, or a flow that a
newer declaration left out.

**Fix:** Name a flow of the declaration, or declare the flow and write the declaration.

**Verify:** `StartFlow` gives a flow of the definition.

## The flow ... is not a name of the form flows/{flow}

```text
INVALID_ARGUMENT: The flow 9f1c2a64-7d1e-4f0a-9a43-1f6c0e2b8d55 is not a name of the form flows/{flow}.
```

**Cause:** `ContinueFlow` with the id of the flow, not its name.

**Fix:** Pass `name` of the flow, such as `flows/9f1c2a64-7d1e-4f0a-9a43-1f6c0e2b8d55`.

**Verify:** `ContinueFlow` gives the flow.

## The flow ... does not exist

```text
NOT_FOUND: The flow flows/9f1c2a64-7d1e-4f0a-9a43-1f6c0e2b8d55 does not exist.
```

**Cause:** `ContinueFlow` of a flow that Pyrite never started, such as one of another database, or of an account that
was deleted, with its flows.

**Fix:** Start a new flow, and answer it under the name that `StartFlow` gives.

**Verify:** `ContinueFlow` gives the flow.

## The session ... does not exist

```text
NOT_FOUND: The session sessions/3d5e7a1c-0f42-4b8e-9c61-2a7d9e4f1b03 does not exist.
```

**Cause:** `GetSession` or `EndSession` of a session that no flow issued, or whose row `Purge` deleted.

**Fix:** Name a session that a flow issued, as `principal.getSession()` or `session` of the `SessionToken` gives it.
A purged session ended before the purge, so its token signs nobody in.

**Verify:** `GetSession` gives the session.

## The request names no credential

```text
INVALID_ARGUMENT: The request names no credential.
```

**Cause:** `Authenticate` without a `session_token`.

**Fix:** Set the session token with `setSessionToken(token)`. For a caller without a token, skip `Authenticate`, and
treat the call as of nobody.

**Verify:** `Authenticate` gives the principal.

## The answer is not a valid ...

```text
INVALID_ARGUMENT: The answer is not a valid pyrite.authentication.v1.PasswordAnswer.
```

**Cause:** An answer whose type URL names the message of a challenge, but whose bytes do not parse as that message.

**Fix:** Pack the answer message with `Any.pack`, such as `Any.pack(PasswordAnswer.newBuilder().setPassword(password)
.build())`.

**Verify:** The answer gives the flow at its next revision, or `PERMISSION_DENIED` when it is wrong.

## The flow ... changed since revision ...

```text
ABORTED: The flow flows/9f1c2a64-7d1e-4f0a-9a43-1f6c0e2b8d55 changed since revision 1.
```

**Cause:** Two answers to the same revision of a flow at the same time. The first one moved the flow on, or counted
as wrong, before the second one was stored.

**Fix:** Send one answer at a time. Read the response of the first, and answer its revision.

**Verify:** The answer gives the flow at its next revision.

## The flow ... has no account

```text
FAILED_PRECONDITION: The flow flows/9f1c2a64-7d1e-4f0a-9a43-1f6c0e2b8d55 has no account: it names none, or the account was deleted.
```

**Cause:** The account of the flow was deleted while the flow went on, and a stage enrols a factor. A flow whose
account was deleted before its end fails with `completes for an account, but has none`, and one whose identifier
went with it fails with `has no identifier`, with the same status.

**Fix:** Start a new flow for an account that exists.

**Verify:** `StartFlow` gives a flow with challenges.

## Internal error

```text
INTERNAL: Internal error
```

**Cause:** A failure that Pyrite does not expect, such as a lost connection to the database while it reads the account
of a session or of a flow. Pyrite then fails the call, and never treats the session as ended or the answer as wrong.
An interceptor that fails a flow with a cause that is not a `PyriteException` gives this status too.

**Fix:** Read the log of the service, under the logger `dev.horizon.pyrite.PyriteException`, at the level `ERROR`.

**Verify:** The call succeeds once the cause in the log is fixed.

## Related

- [About accounts and flows](about-accounts-and-flows.md)
- [Sign in with a password](sign-in-with-a-password.md)
- [Status codes](../deployment/status-codes.md)
