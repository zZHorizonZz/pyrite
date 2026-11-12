# Options

`PyriteOptions` holds the limits of Pyrite, the key of its page tokens, and the options of its keys and of
authentication. Each option has a setter that returns the options, a getter, and a `DEFAULT_` constant for its
default; each class of options has a copy constructor. `Pyrite.pyrite` copies the options, so a change after it does
not reach the running Pyrite. Give every Pyrite on the same database the same options. See
[Serve the services](serve-the-services.md).

```java
PyriteOptions options = new PyriteOptions()
    .setMaxPageSize(500)
    .setKeyOptions(new KeyOptions().setRoot(root))
    .setAuthenticationOptions(new AuthenticationOptions()
        .setMacKey("keys/sessions")
        .setEncryptionKey("keys/factors")
        .setIssuer("Acme")
        .setRelyingPartyOptions(new RelyingPartyOptions().setId("acme.dev").setName("Acme")
            .addOrigin("https://acme.dev")));
```

A setter throws an `IllegalArgumentException` for a value that no Pyrite runs with, such as a lifetime of zero, and a
`NullPointerException` for `null`.

## PyriteOptions

| Option                  | Default                       | Sets                                                             | Limits     |
| ----------------------- | ----------------------------- | ---------------------------------------------------------------- | ---------- |
| `defaultPageSize`       | `50`                          | The page size of a list that names none, up to `maxPageSize`     | At least 1 |
| `maxPageSize`           | `1000`                        | The largest page; a request for a larger one gets this size      | At least 1 |
| `tuplesPerRead`         | `100`                         | The most tuples that Pyrite reads from OpenFGA in one call       | At least 1 |
| `pageTokenKey`          | A random 256-bit AES key      | The `SecretKey` that Pyrite's database encrypts page tokens with | Not null   |
| `keyOptions`            | `new KeyOptions()`            | The options of keys                                              | Not null   |
| `authenticationOptions` | `new AuthenticationOptions()` | The options of accounts, sessions and flows                      | Not null   |

`ListGrants` and `ListChanges` read a page of tuples from OpenFGA, so their pages have at most `tuplesPerRead` items,
whatever the page size, and their page tokens are the continuation tokens of OpenFGA.

Each `new PyriteOptions()` makes a new random `pageTokenKey`. Give every Pyrite on the same database the same key.

## KeyOptions

| Option      | Default     | Sets                                                                                 | Limits       |
| ----------- | ----------- | ------------------------------------------------------------------------------------ | ------------ |
| `root`      | None        | The Tink `Aead` that seals the material of key versions                              | Not null     |
| `clockSkew` | `5 seconds` | How far the `exp` and `nbf` of a JWT may be off, when a verification names no leeway | Not negative |

A declaration with `pyrite.dev/Key` needs a root key. The database is the clock of every time that Pyrite stores; the
clock skew covers only the clocks of the servers whose JWTs Pyrite verifies.

## AuthenticationOptions

A declaration with `pyrite.dev/Account` needs `macKey`, `encryptionKey`, `issuer`, and the id and name of the relying
party. Writing such a declaration without them fails with `FAILED_PRECONDITION`, and the message names what is
missing; `missing()` gives the same names.

| Option                | Default                     | Sets                                                                             | Limits     |
| --------------------- | --------------------------- | -------------------------------------------------------------------------------- | ---------- |
| `macKey`              | None                        | The name of the key that signs session tokens and codes, such as `keys/sessions` | Not null   |
| `encryptionKey`       | None                        | The name of the key that encrypts the secrets of factors, such as `keys/factors` | Not null   |
| `relyingPartyOptions` | `new RelyingPartyOptions()` | The WebAuthn relying party of passkeys                                           | Not null   |
| `issuer`              | None                        | The issuer that authenticator apps show next to a TOTP code, such as `Acme`      | Not null   |
| `sessionLifetime`     | `30 days`                   | How long a session lasts after it is issued                                      | Positive   |
| `idleLifetime`        | `7 days`                    | How long a session lasts after its last authentication, up to its lifetime       | Positive   |
| `flowLifetime`        | `15 minutes`                | How long a flow lasts after it starts, and an enrolment that no flow finishes    | Positive   |
| `codeLifetime`        | `10 minutes`                | How long a code lasts after Pyrite sends it                                      | Positive   |
| `maxAnswers`          | `5`                         | The most wrong answers that a flow takes                                         | At least 1 |
| `argon2Options`       | `new Argon2Options()`       | The cost of password hashes                                                      | Not null   |

The keys that `macKey` and `encryptionKey` name are resources of `pyrite.dev/Key`. Create them with `CreateKey` after
the first declaration: the key of `macKey` with `HMAC_SHA256`, and the key of `encryptionKey` with `AES256_GCM`.

## RelyingPartyOptions

| Option    | Default | Sets                                                                                             |
| --------- | ------- | ------------------------------------------------------------------------------------------------ |
| `id`      | None    | The WebAuthn relying party id, your domain, such as `acme.dev`                                   |
| `name`    | None    | The name that authenticators show, such as `Acme`                                                |
| `origins` | Empty   | The origins that passkey answers may come from, such as `https://acme.dev`; `addOrigin` adds one |

## Argon2Options

Pyrite hashes passwords with Argon2id. The defaults are the second recommended option of RFC 9106.

| Option        | Default | Sets                       | Limits     |
| ------------- | ------- | -------------------------- | ---------- |
| `memory`      | `65536` | The memory, in KiB         | At least 1 |
| `iterations`  | `3`     | The passes over the memory | At least 1 |
| `parallelism` | `4`     | The lanes                  | At least 1 |

## Related

- [Serve the services](serve-the-services.md)
- [About keys and versions](../keys/about-keys-and-versions.md)
- [About accounts and flows](../accounts/about-accounts-and-flows.md)
- [Troubleshooting deployment](troubleshooting-deployment.md)
