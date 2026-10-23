# Accounts and flows

An account is a resource of the type `pyrite.dev/Account`, such as `accounts/anne`. Its principal is `user:anne`. The
authentication service proves that a caller holds an account, through flows that your declaration defines, and
issues sessions.

## Accounts and what they hold

Accounts are at the top of the hierarchy. Beneath each account are three types of Pyrite's:

| Type                    | Example            | Holds                                                           |
| ----------------------- | ------------------ | --------------------------------------------------------------- |
| `pyrite.dev/Identifier` | `identifiers/work` | An email address, a phone number or a username, and if verified |
| `pyrite.dev/Factor`     | `factors/<id>`     | A password, a TOTP secret, a passkey or a set of recovery codes |
| `pyrite.dev/Session`    | `sessions/<id>`    | A signed-in session, with its expiry times                      |

You create accounts and identifiers with the data service. Factors come only from enrolment flows, and sessions only
from flows that issue them. An account's kind is `HUMAN` or `SERVICE`.

When Pyrite creates an account, it grants the account the relation `owner` on itself. Declare `owner` so that it
takes a user directly, and build `admin` and `viewer` from it. Then each person administers their own account.

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
digits, and returns it in the flow's `deliveries`, with the channel and the address. Pyrite does not send messages.
Your application sends the code by email or SMS, and the person answers it with a `CodeAnswer`. A code expires after
`codeLifetime`, 10 minutes by default.

## Unknown identifiers

A flow started with an identifier that no account has does not fail. It offers the same answer methods as a flow of
a real account, and no answer is ever right. It has no deliveries, and its code challenge has no `CodeSent`
parameters. Show the person the same page either way, so that your application does not tell whether an account
exists.

## Sessions

A session is issued by a flow, and its token is returned once, in the result's `issued`, as a `SessionToken`. Pyrite
stores only a MAC of the token's secret, made with the MAC key that the options name.

`Authenticate` takes the token and returns the session's `Principal`. A session ends at the first of these:

- Its `expire_time`: `sessionLifetime` after it was issued, 30 days by default.
- Its idle expiry: `idleLifetime` after it was last authenticated, 7 days by default.
- An `END_SESSIONS` completion, or a `DeleteResource` call on the session.

`Authenticate` also fails when the session's account is deleted.

## Secrets

Factor secrets, such as a TOTP secret, are encrypted with the encryption key that the options name. Passwords are
hashed with Argon2id. A declaration with accounts therefore needs `pyrite.dev/Key`, a MAC key and an encryption key.
For the options, refer to [Options](../reference/options.md).

## What a flow is not

- A flow is not a session. It ends when its last stage is met, and a session is one of its results.
- A flow does not send email or SMS. It returns the codes for your application to send.
- A flow is not a login form. Your application renders the challenges and sends the answers.
