# About accounts and exchanges

An account is a resource of the type `pyrite.dev/Account`, such as `accounts/anne`, whose principal is `user:anne`. The
authentication service keeps accounts, proves through exchanges that a caller holds one, and issues sessions. The
declaration defines each exchange: what it starts from, what each of its stages requires, and what it does at the end.

```textproto
exchanges { name: "sign-in" input: IDENTIFIER stages { factors: 1 } completes: ISSUE_SESSION }
```

`StartExchange` of `sign-in` with `anne@acme.dev` gives an exchange whose challenges name the answers that its stage
takes, such as `pyrite.authentication.v1.PasswordAnswer`. `ContinueExchange` with Anne's password meets the stage. The
exchange is done, and its result holds a `TokenSet`. `Authenticate` with its access token gives the principal
`user:anne` on later calls.

## Accounts and what they hold

Accounts are at the top of the hierarchy: `pyrite.dev/Account` has no parents. Each account holds three kinds of
records, which the authentication service keeps under it. They are not declared types, so a call on them checks a
relation on their account.

| Record     | Name               | Holds                                                                            |
| ---------- | ------------------ | -------------------------------------------------------------------------------- |
| Identifier | `identifiers/<id>` | A value of a declared kind, such as an email address, and whether it is verified |
| Factor     | `factors/<id>`     | A password, a TOTP secret, a passkey or a set of recovery codes                  |
| Session    | `sessions/<id>`    | A session that an exchange issued, with its expiry                               |

| Call              | Relation on the account |
| ----------------- | ----------------------- |
| `CreateAccount`   | None                    |
| `GetAccount`      | `viewer`                |
| `ListIdentifiers` | `viewer`                |
| `GetSession`      | `viewer`                |
| `AddIdentifier`   | `admin`                 |
| `EndSession`      | `admin`                 |
| `DeleteAccount`   | `admin`                 |

`CreateAccount` makes an account of the kind `HUMAN` or `SERVICE`, under the id of the request, such as `anne` for
`accounts/anne`. It grants the account the relation `owner` on itself, and grants the subject of the request the
type's `creator` relation, if the type has one. Declare `owner` so that it takes a user directly, and build `admin`
and `viewer` from it: then each person administers their own account. `CreateAccount` checks no relation, because an
account has no parent to check it on, so call it from your own sign-up code.

`AddIdentifier` adds an identifier, unverified. An identifier belongs to one account: a value that an account has
already, after its kind normalizes it, fails with `ALREADY_EXISTS`. Factors come only from exchanges that enrol them,
and sessions only from exchanges that issue them. `DeleteAccount` deletes the account with its identifiers, factors,
sessions and exchanges, and every grant on it and to it.

## Identifier kinds

Each identifier has a kind that the declaration names, such as `email`, `phone` or `username`. Pyrite has no kinds of
its own, and no rules of its own for what an address or a number looks like. The CEL expressions of each kind decide:

- **`match`** decides whether a value is of the kind.
- **`normalize`** gives the value that Pyrite stores and compares, such as the address trimmed and in lower case.
  The normalized value must still meet `match`.
- **`receives_codes`** decides whether a stage with `identifier` sends a code to an identifier of the kind.

`AddIdentifier` takes the kind in `kind`, and `StartExchange` in `identifier_kind`. A request that names no kind takes
the first kind, in the order of the declaration, whose `match` the value meets. With the kinds `email`, `phone` and
`username`, in that order:

| The person types   | Kind       | Pyrite stores and compares |
| ------------------ | ---------- | -------------------------- |
| `Anne@Acme.dev`    | `email`    | `anne@acme.dev`            |
| `+420 777 123 456` | `phone`    | `+420777123456`            |
| `Anne.K`           | `username` | `anne.k`                   |

Declare the strictest kinds first. A kind whose `match` takes almost any value belongs last, or it hides the kinds
after it.

| Request                                                                        | Result                                           |
| ------------------------------------------------------------------------------ | ------------------------------------------------ |
| Names a kind that the declaration does not have                                | `INVALID_ARGUMENT`                               |
| Names a kind, and the value does not meet its `match`                          | `INVALID_ARGUMENT`                               |
| Names no kind, and no kind matches, in `AddIdentifier`                         | `INVALID_ARGUMENT`                               |
| Names no kind, and no kind matches, in `StartExchange` of input `IDENTIFIER`   | An exchange of no account, as for an unknown one |
| Names no kind, and no kind matches, in `StartExchange` of `SUBJECT_IDENTIFIER` | `NOT_FOUND`                                      |
| Has a blank identifier, in `StartExchange` of an exchange that takes one       | `INVALID_ARGUMENT`                               |

`StartExchange` does not fail for a value of no kind, so that a sign-in page does not tell what Pyrite accepts. See
[Unknown identifiers](#unknown-identifiers).

## Exchanges

An exchange is one run of an exchange definition of the declaration, such as `sign-in`. The definition names three
things:

- **The input:** what the caller starts with. `IDENTIFIER` is an identifier, such as an email address. `SUBJECT` is
  the account that calls, as the `subject` of the request. `SUBJECT_IDENTIFIER` is both, and the identifier must be
  one of that account's.
- **The stages:** what has to be proven, in order.
- **The completions:** what the exchange does when its last stage is met, in order.

| Completion        | Does                                            | Needs                                                                       |
| ----------------- | ----------------------------------------------- | --------------------------------------------------------------------------- |
| `ISSUE_SESSION`   | Issues a session, and gives its token once      | Input `IDENTIFIER`, and a stage of factors                                  |
| `MARK_VERIFIED`   | Marks the identifier as verified                | Input `SUBJECT_IDENTIFIER`, and an `identifier` stage                       |
| `ACTIVATE_FACTOR` | Activates the factor that the exchange enrolled | An `enrol` stage                                                            |
| `END_SESSIONS`    | Ends every live session of the account          | Input `SUBJECT` or `SUBJECT_IDENTIFIER`, or a stage that proves the account |

`WriteDeclaration` refuses an exchange whose completions lack what they need. A code sent to an identifier is no factor,
so an exchange cannot issue a session on a code alone.

An exchange that is done has a `result`:

| Field         | Holds                                                                    |
| ------------- | ------------------------------------------------------------------------ |
| `account`     | The account of the exchange, such as `accounts/anne`                     |
| `identifiers` | The identifiers that `MARK_VERIFIED` verified                            |
| `factors`     | The factors that `ACTIVATE_FACTOR` activated                             |
| `sessions`    | The sessions that `ISSUE_SESSION` issued                                 |
| `issued`      | The credentials that Pyrite gives once: a `TokenSet`, or `RecoveryCodes` |

## Stages and factors

A stage requires one or more of these:

| Requirement          | Met by                                                                                       |
| -------------------- | -------------------------------------------------------------------------------------------- |
| `factors: N`         | Answers that prove N of knowledge, possession, inherence and a sign-in at a provider, 0 to 3 |
| `phishing_resistant` | A passkey                                                                                    |
| `identifier`         | The code that Pyrite sent to the identifier of the exchange                                  |
| `enrol`              | A new factor of that kind, in a stage of its own                                             |

| Answer               | Proves                                                                 |
| -------------------- | ---------------------------------------------------------------------- |
| `PasswordAnswer`     | Knowledge                                                              |
| `TotpAnswer`         | Possession                                                             |
| `RecoveryCodeAnswer` | Possession                                                             |
| `PasskeyAnswer`      | Possession, and inherence too when the authenticator verified the user |
| `ProviderAnswer`     | A sign-in at a provider, which counts as a factor of its own           |

A stage takes only the answers that add what it still lacks. `factors: 2` therefore takes a password and a TOTP code,
never two passwords, and a passkey that verified the user meets it alone. `factors: 1` takes any one factor: a
password, a TOTP code, a recovery code or a passkey. A stage with `phishing_resistant` takes a passkey before any
other answer.

An enrolment of a factor is a stage of its own, and comes after the account is proven: by the input `SUBJECT`, or by an
earlier stage, as in a recovery. An exchange enrols one factor at most. A new password, TOTP secret or set of recovery
codes replaces the active one of its kind; passkeys add up.

## Challenges and answers

`StartExchange` and `ContinueExchange` give the exchange with its `challenges`: the answers that it takes next. Each
challenge names its answer message in `method`, such as `pyrite.authentication.v1.PasswordAnswer`. Some carry
`parameters`: `TotpProvisioning` with the `otpauth` URI of a TOTP enrolment, `PasskeyOptions` with the WebAuthn options
of a passkey, and `CodeSent` with the identifier that a code went to.

`ContinueExchange` answers one challenge per call:

- **`revision`** is the revision of the exchange that you answer. Each right answer moves the exchange to the next
  revision, and an answer at an older one fails with `ABORTED`.
- **`request_id`** is an id that you choose. A retry with the id of the last right answer gives its response again,
  and does nothing more.
- **`answer`** is the answer message, packed in an `Any`.

A wrong answer fails with `PERMISSION_DENIED`, leaves the exchange at its revision, and counts against the exchange.
After `maxAnswers` wrong answers, 5 by default, the exchange refuses every answer with `RESOURCE_EXHAUSTED`, the right
one too. An exchange expires `exchangeLifetime` after it starts, 15 minutes by default, on the clock of the database.

## Codes

A stage with `identifier` proves that the caller receives messages at the identifier of the exchange. When the exchange
reaches the stage, Pyrite makes a code of six digits, keeps only its MAC, and gives the code in the exchange's
`deliveries`. Only an identifier whose kind has `receives_codes` gets a code. It must also be verified, except in an
exchange whose input is `SUBJECT_IDENTIFIER`, which is how an identifier comes to be verified.

| Field of `Delivery` | Holds                                                                          |
| ------------------- | ------------------------------------------------------------------------------ |
| `to`                | The value of the identifier, such as `anne@acme.dev`                           |
| `code`              | The code, six digits                                                           |
| `expire_time`       | When the code expires: `codeLifetime` after it was made, 10 minutes by default |
| `kind`              | The kind of the identifier, such as `email`                                    |
| `identifier`        | The name of the identifier, such as `identifiers/0b0d...`                      |

Pyrite sends no messages. Your application picks how to send each code by its `kind`, such as a mail for `email` and
a text message for `phone`, and the person answers with a `CodeAnswer`. A code interceptor can send the codes
instead, so that no response carries one. See [Intercept exchanges](intercept-exchanges.md).

The code challenge carries `CodeSent` parameters: the `identifier` that the code went to, and its `kind`. Pyrite does
not mask the value. Your application has it in the delivery, and shows the person where the code went in the form
that it chooses, such as `a***@acme.dev`.

## Unknown identifiers

An exchange that starts from an identifier that no account has does not fail. Neither does an exchange that starts from
a value of no declared kind, when the request names no kind. Such an exchange takes the same answers as an exchange of a
real account, none of them right, and runs out of answers the same way. A password answer takes as long, because Pyrite
hashes a password that no account has. The exchange has no deliveries, and its code challenge has no `CodeSent`
parameters. Show the person the same page either way, so that your application does not tell whether an account exists.

## Sessions

A session is the server's record of one sign-in. An exchange with `ISSUE_SESSION` issues one, and gives its first
tokens once, in `issued` of its result, as a `TokenSet`:

- The **access token** is a JWT of type `at+jwt`, which the access token key of the options signs. Its claims are
  `iss`, the issuer of the options; `sub`, the subject of the account, such as `user:anne`; `sid`, the session; `iat`,
  `exp` and `jti`. It lasts `accessTokenLifetime`, 5 minutes by default. A caller sends it with each call.
- The **refresh token** is opaque: the id of its row, a dot, and a random secret. Pyrite keeps only a MAC of the secret,
  made with the MAC key of the options. A client keeps it to itself, and trades it with `RefreshSession` for a new
  access token and a new refresh token before the access token expires.

A refresh token works once. A refresh marks it as used in the same statement that checks it, so two refreshes with
one token cannot both succeed; a token that comes back after its trade was copied, so Pyrite ends its session, and the
newest tokens stop working too.

`Authenticate` takes an access token, checks its signature, its type, its issuer and its expiry, and that its session
has not ended, and gives the `Principal` of the session: the subject, the account, the session and its `expire_time`.
A service can also verify an access token on its own, with the public key of the access token key; it then takes the
token until its `exp`, even after the session ends, which is why the token is short-lived.

A session ends at the first of these, on the clock of the database:

- Its `expire_time`: `sessionLifetime` after it was issued, 30 days by default, however often it is refreshed.
- Its idle expiry: `idleLifetime` after its last refresh, 7 days by default. Each refresh moves the idle expiry on,
  at most once a minute.
- An `EndSession` call, or an exchange with `END_SESSIONS`.
- A refresh token that comes back after its trade.
- The deletion of its account.

`Purge` deletes the rows of sessions that were ended or passed their `expire_time`, with the factors that were
replaced and those whose enrolment was never finished. A session that idled out keeps its row until its
`expire_time`. See [Purge deleted resources](../access/purge-deleted-resources.md).

## Secrets

Pyrite hashes passwords with Argon2id, and encrypts TOTP secrets with the encryption key that the options name. It
keeps the MACs of refresh tokens, codes and recovery codes, made with the MAC key, and never the values. A
declaration with accounts therefore needs `pyrite.dev/Key`, and options of authentication with a MAC key, an
encryption key, an issuer and a relying party. See [Options](../deployment/options.md).

## What an exchange is not

- An exchange is not a session. It ends when its last stage is met, and a session is one of its results.
- An exchange does not send messages. It gives the codes, and your application sends each one by the kind of its
  identifier.
- An exchange is not a login form. Your application renders the challenges and sends the answers, so one exchange serves
  a web page, a mobile app and a command line alike.

## Related

- [Sign in with a password](sign-in-with-a-password.md)
- [Declaration reference](../declarations/declaration-reference.md)
- [Troubleshooting accounts](troubleshooting-accounts.md)
