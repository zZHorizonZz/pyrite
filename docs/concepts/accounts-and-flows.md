# Accounts and flows

An account is a resource of the type `pyrite.dev/Account`, such as `accounts/anne`. Its principal is `user:anne`. The
authentication service keeps accounts, proves that a caller holds one through flows that your declaration defines,
and issues sessions.

## Accounts and what they hold

Accounts are at the top of the hierarchy. Each account holds three kinds of records, which the authentication service
keeps with it. They are not declared types: every call on them checks a relation on their account.

| Record     | Example            | Holds                                                                 |
| ---------- | ------------------ | --------------------------------------------------------------------- |
| Identifier | `identifiers/<id>` | A value of a declared kind, such as an email address, and if verified |
| Factor     | `factors/<id>`     | A password, a TOTP secret, a passkey or a set of recovery codes       |
| Session    | `sessions/<id>`    | A signed-in session, with its expiry times                            |

`CreateAccount` creates an account, of the kind `HUMAN` or `SERVICE`. `AddIdentifier` adds an identifier, unverified.
Factors come only from enrolment flows, and sessions only from flows that issue them.

| Call              | Relation on the account |
| ----------------- | ----------------------- |
| `GetAccount`      | `viewer`                |
| `ListIdentifiers` | `viewer`                |
| `GetSession`      | `viewer`                |
| `AddIdentifier`   | `admin`                 |
| `EndSession`      | `admin`                 |
| `DeleteAccount`   | `admin`                 |

When Pyrite creates an account, it grants the account the relation `owner` on itself, and grants the subject that
created it the type's `creator` relation, if the type has one. Declare `owner` so that it takes a user directly, and
build `admin` and `viewer` from it. Then each person administers their own account.

An identifier belongs to one account. `AddIdentifier` with an identifier that any account has already fails with
`ALREADY_EXISTS`. `DeleteAccount` deletes the account with its identifiers, factors and sessions, and every grant on
it and to it.

## Identifier kinds

Each identifier has a kind that the declaration names, such as `email`, `phone` or `username`. Pyrite has no kinds
of its own, and no rules of its own for what an address or a number looks like. Each kind's CEL expressions decide:

- **`match`** decides whether a value is of the kind.
- **`normalize`** gives the value that Pyrite stores and compares, such as the address trimmed and lowercased. The
  normalized value must still meet `match`.
- **`receives_codes`** decides whether a stage with `identifier` sends a code to an identifier of the kind.

`AddIdentifier` takes the kind in `kind`, and `StartFlow` in `identifier_kind`. Leave it empty, and Pyrite takes the
first kind, in declaration order, whose `match` the value meets. With the kinds `email`, `phone` and `username`, in
that order:

| The person types   | Kind       | Pyrite stores and compares |
| ------------------ | ---------- | -------------------------- |
| `Anne@Acme.dev`    | `email`    | `anne@acme.dev`            |
| `+420 777 123 456` | `phone`    | `+420777123456`            |
| `Anne.K`           | `username` | `anne.k`                   |

Declare the strictest kinds first. A kind whose `match` takes almost any value belongs last, or it hides the kinds
after it.

| Request                                                         | Result                                                   |
| --------------------------------------------------------------- | -------------------------------------------------------- |
| Names a kind that the declaration does not have                 | `INVALID_ARGUMENT`                                       |
| Names a kind, and the value does not meet its `match`           | `INVALID_ARGUMENT`                                       |
| Names no kind, and no kind matches, in `AddIdentifier`          | `INVALID_ARGUMENT`                                       |
| Names no kind, and no kind matches, in `StartFlow`              | A flow that finds no account, as for unknown identifiers |
| Has a blank identifier, in `StartFlow` of a flow that takes one | `INVALID_ARGUMENT`                                       |

`StartFlow` does not fail for a value of no kind, so that a sign-in page does not tell what Pyrite accepts. For more
information, refer to [Unknown identifiers](#unknown-identifiers).

## Flows

A flow is one run of a flow definition, such as `sign-in`. The definition names three things:

- **The input:** what the caller starts with. `IDENTIFIER` is an identifier, such as an email address. `SUBJECT` is
  the account that calls. `SUBJECT_IDENTIFIER` is both.
- **The stages:** what has to be proven, in order.
- **The completions:** what happens when the last stage is met.

| Completion        | Does                                         | Needs                                           |
| ----------------- | -------------------------------------------- | ----------------------------------------------- |
| `ISSUE_SESSION`   | Issues a session, and returns its token once | Input `IDENTIFIER`, and a stage of factors      |
| `MARK_VERIFIED`   | Marks the identifier as verified             | Input `SUBJECT_IDENTIFIER`, an identifier stage |
| `ACTIVATE_FACTOR` | Activates the factor that the flow enrolled  | An enrolment stage                              |
| `END_SESSIONS`    | Ends every session of the account            | A proven account                                |

`WriteDeclaration` refuses a flow whose completions are not backed by its input and stages.

## Results

A flow that is done has a `result`:

| Field         | Holds                                                              |
| ------------- | ------------------------------------------------------------------ |
| `account`     | The account of the flow, such as `accounts/anne`                   |
| `identifiers` | The identifiers that `MARK_VERIFIED` verified                      |
| `factors`     | The factors that `ACTIVATE_FACTOR` activated                       |
| `sessions`    | The sessions that `ISSUE_SESSION` issued                           |
| `issued`      | The credentials shown once, as a `SessionToken` or `RecoveryCodes` |

## Stages

A stage requires one or more of these:

| Requirement          | Met by                                                      |
| -------------------- | ----------------------------------------------------------- |
| `factors: N`         | Answers that prove N of knowledge, possession and inherence |
| `phishing_resistant` | A passkey                                                   |
| `identifier`         | The code that Pyrite generated for the identifier           |
| `enrol`              | A new factor of that kind; a stage of its own               |

A password proves knowledge. A TOTP code and a recovery code prove possession. A passkey proves possession, and
inherence when the authenticator verified the user. Two factors therefore take, for example, a password and a TOTP
code, never two passwords.

## Challenges and answers

`StartFlow` and `ContinueFlow` return the flow with its `challenges`: the answers that it takes next. Each challenge
names its answer message, such as `pyrite.authentication.v1.PasswordAnswer`. Some carry parameters, such as the
`otpauth` URI of a TOTP enrolment.

You answer one challenge per call to `ContinueFlow`:

- **`revision`** is the flow's revision that you answer. A flow that moved on fails with `ABORTED`.
- **`request_id`** is an id that you choose. A retry with the same id returns the first result.
- **`answer`** is the answer message, packed in an `Any`.

A wrong answer fails with `PERMISSION_DENIED`, and counts against the flow. After `maxAnswers` answers, 5 by
default, the flow fails with `RESOURCE_EXHAUSTED`. A flow expires after `flowLifetime`, 15 minutes by default.

## Codes

A stage with `identifier` proves that the caller receives messages at the identifier. Pyrite generates a code of six
digits, and returns it in the flow's `deliveries`. Only an identifier whose kind has `receives_codes` gets a code. It
must also be verified, except in a flow whose input is `SUBJECT_IDENTIFIER`, which is how an identifier gets
verified.

| Field of `Delivery` | Holds                                                              |
| ------------------- | ------------------------------------------------------------------ |
| `to`                | The identifier's value, such as `anne@acme.dev`                    |
| `code`              | The code, six digits                                               |
| `expire_time`       | When the code expires: after `codeLifetime`, 10 minutes by default |
| `kind`              | The identifier's kind, such as `email`                             |
| `identifier`        | The identifier's name, such as `identifiers/0b0d...`               |

Pyrite does not send messages. Your application picks how to send the code from `kind`, such as a mail for `email`
and a text message for `phone`. The person answers with a `CodeAnswer`.

The code challenge carries `CodeSent` parameters: the `identifier` that the code went to, and its `kind`. Pyrite does
not mask the value. Your application has it in the delivery, and shows the person where the code went in the form it
chooses, such as `a***@acme.dev`.

## Unknown identifiers

A flow started with an identifier that no account has does not fail. Neither does a flow started with a value of no
declared kind, when the request names no kind. Such a flow offers the same answer methods as a flow of a real account,
and no answer is ever right. It has no deliveries, and its code challenge has no `CodeSent` parameters. Show the
person the same page either way, so that your application does not tell whether an account exists.

## Sessions

A session is issued by a flow, and its token is returned once, in the result's `issued`, as a `SessionToken`. Pyrite
stores only a MAC of the token's secret, made with the MAC key that the options name.

`Authenticate` takes the token and returns the session's `Principal`. A session ends at the first of these:

- Its `expire_time`: `sessionLifetime` after it was issued, 30 days by default.
- Its idle expiry: `idleLifetime` after it was last authenticated, 7 days by default.
- An `END_SESSIONS` completion, or an `EndSession` call.

`Authenticate` also fails when the session's account is deleted.

An ended session stays in its table until `Purge` deletes it, with the factors whose enrolment was never finished.
Call `Purge` now and then. For more information, refer to
[Purge deleted resources](../how-to/purge-deleted-resources.md).

## Secrets

Factor secrets, such as a TOTP secret, are encrypted with the encryption key that the options name. Passwords are
hashed with Argon2id. A declaration with accounts therefore needs `pyrite.dev/Key`, a MAC key and an encryption key.
For the options, refer to [Options](../reference/options.md).

## What a flow is not

- A flow is not a session. It ends when its last stage is met, and a session is one of its results.
- A flow does not send messages. It returns the codes, and your application sends each one by its identifier's
  kind.
- A flow is not a login form. Your application renders the challenges and sends the answers.
