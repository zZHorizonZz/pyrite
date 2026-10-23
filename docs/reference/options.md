# Options

`PyriteServiceOptions` holds the limits, the keys and the clock of Pyrite's services. `new PyriteServiceOptions()`
has the defaults below. Each `with` method gives a copy with one value changed. Give every service on the same
database the same options. For how to pass them, refer to [Serve the services](../how-to/serve-the-services.md).

## PyriteServiceOptions

| Option            | Method               | Default             | Sets                                                       |
| ----------------- | -------------------- | ------------------- | ---------------------------------------------------------- |
| `defaultPageSize` | none                 | `50`                | The page size of a list or query that names none           |
| `maxPageSize`     | none                 | `1000`              | The largest page; a larger request gets this size          |
| `maxDepth`        | none                 | `64`                | The ancestors that a check or query walks, at most         |
| `tuplesPerRead`   | none                 | `100`               | The tuples that Pyrite reads from OpenFGA per call         |
| `pageTokenKey`    | none                 | A random AES key    | The key that encrypts page tokens                          |
| `root`            | `withRoot`           | None                | The Tink `Aead` that encrypts the material of key versions |
| `authentication`  | `withAuthentication` | None                | The options of accounts, sessions and flows                |
| `clock`           | `withClock`          | `Clock.systemUTC()` | The clock of every time that Pyrite stores                 |

The options without a method are set with the record's constructor:

```java
PyriteServiceOptions options = new PyriteServiceOptions(50, 500, 32, 100, pageTokenKey, Optional.of(root),
    Optional.empty(), Clock.systemUTC());
```

Every limit is at least `1`, and `maxPageSize` is at least `defaultPageSize`.

Each `new PyriteServiceOptions()` makes a new random `pageTokenKey`. A page token works only at a service with the
key that made it, so give every service that serves the same callers the same options.

## Authentication

`PyriteServiceOptions.Authentication` holds the options of the authentication service. Its short constructor takes
the first four values, and the defaults for the rest.

| Option            | Default      | Sets                                                                                 |
| ----------------- | ------------ | ------------------------------------------------------------------------------------ |
| `macKey`          | None         | The name of the MAC key that signs session tokens and codes, such as `keys/sessions` |
| `encryptionKey`   | None         | The name of the encryption key of factor secrets, such as `keys/factors`             |
| `relyingParty`    | None         | The WebAuthn relying party of passkeys                                               |
| `issuer`          | None         | The issuer that authenticator apps show next to a TOTP code                          |
| `sessionLifetime` | `30 days`    | How long a session lasts after it is issued                                          |
| `idleLifetime`    | `7 days`     | How long a session lasts after its last `Authenticate`                               |
| `flowLifetime`    | `15 minutes` | How long a flow lasts after it starts                                                |
| `codeLifetime`    | `10 minutes` | How long a code lasts after Pyrite generates it                                      |
| `maxAnswers`      | `5`          | The answers that a flow takes before it fails                                        |
| `argon2`          | `RFC_9106`   | The cost of password hashes                                                          |

Every lifetime is positive, and `maxAnswers` is at least `1`. The keys that `macKey` and `encryptionKey` name are
resources of `pyrite.dev/Key`: create them after the first declaration, with the purposes `MAC` and `ENCRYPTION`.

## RelyingParty

| Option    | Sets                                                                       |
| --------- | -------------------------------------------------------------------------- |
| `id`      | The WebAuthn relying party id, your domain, such as `acme.dev`             |
| `name`    | The name that authenticators show, such as `Acme`                          |
| `origins` | The origins that passkey answers may come from, such as `https://acme.dev` |

## Argon2

Passwords are hashed with Argon2id. `Argon2.RFC_9106` is the second recommended option of RFC 9106.

| Option        | `RFC_9106` | Sets                       |
| ------------- | ---------- | -------------------------- |
| `memory`      | `65536`    | The memory in KiB          |
| `iterations`  | `3`        | The passes over the memory |
| `parallelism` | `4`        | The lanes                  |

A password has 8 to 128 characters, as NIST SP 800-63B asks.
