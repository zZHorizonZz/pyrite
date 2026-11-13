# Options

`PyriteOptions` holds the limits of Pyrite, the key of its page tokens, and the options of its keys and of
authentication. Each option has a setter that returns the options and a getter, and each option of a number or a
duration has a `DEFAULT_` constant for its default; each class of options has a copy constructor. `Pyrite.pyrite` copies
the options, so a change after it does not reach the running Pyrite. Give every Pyrite on the same database the same
options. See [Serve the services](serve-the-services.md).

```java
PyriteOptions options = new PyriteOptions()
    .setMaxPageSize(500)
    .setKeyOptions(new KeyOptions().setRoot(root))
    .setAuthenticationOptions(new AuthenticationOptions()
        .setMacKey("keys/sessions")
        .setEncryptionKey("keys/factors")
        .setAccessTokenKey("keys/access")
        .setIssuer("Acme")
        .setRelyingPartyOptions(new RelyingPartyOptions().setId("acme.dev").setName("Acme")
            .addOrigin("https://acme.dev")));
```

A setter throws an `IllegalArgumentException` for a value that no Pyrite runs with, such as a lifetime of zero, and a
`NullPointerException` for `null`.

## PyriteOptions

| Option                  | Default                       | Sets                                                             | Limits                             |
| ----------------------- | ----------------------------- | ---------------------------------------------------------------- | ---------------------------------- |
| `defaultPageSize`       | `50`                          | The page size of a list that names none, up to `maxPageSize`     | At least 1                         |
| `maxPageSize`           | `1000`                        | The largest page; a request for a larger one gets this size      | At least 1                         |
| `tuplesPerRead`         | `100`                         | The most tuples that Pyrite reads from OpenFGA in one call       | At least 1                         |
| `pageTokenKey`          | A random 256-bit AES key      | The `SecretKey` that Pyrite's database encrypts page tokens with | An AES key of 128, 192 or 256 bits |
| `keyOptions`            | `new KeyOptions()`            | The options of keys                                              | Not null                           |
| `authenticationOptions` | `new AuthenticationOptions()` | The options of accounts, sessions and exchanges                  | Not null                           |

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

A declaration with `pyrite.dev/Account` needs `macKey`, `encryptionKey`, `accessTokenKey`, `issuer`, and the id, the
name and at least one origin of the relying party. Writing such a declaration without them fails with
`FAILED_PRECONDITION`, and the message names what is missing; `missing()` gives the same names.

| Option                | Default                     | Sets                                                                                                                      | Limits                                                                            |
| --------------------- | --------------------------- | ------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| `macKey`              | None                        | The name of the key that signs refresh tokens and codes, such as `keys/sessions`                                          | Not null                                                                          |
| `encryptionKey`       | None                        | The name of the key that encrypts the secrets of factors, such as `keys/factors`                                          | Not null                                                                          |
| `accessTokenKey`      | None                        | The name of the key that signs access tokens, of `ES256`, `EDDSA` or `RS256`, such as `keys/access`; give it no other use | Not null                                                                          |
| `accessTokenLifetime` | `5 minutes`                 | How long an access token lasts; a service that verifies it on its own takes it until then                                 | Positive                                                                          |
| `relyingPartyOptions` | `new RelyingPartyOptions()` | The WebAuthn relying party of passkeys                                                                                    | Not null                                                                          |
| `providers`           | Empty                       | The providers that people sign in at; `addProvider` adds one                                                              | Each with a name, a Vert.x provider and a callback, and no two with the same name |
| `issuer`              | None                        | The issuer that authenticator apps show next to a TOTP code, such as `Acme`                                               | Not null                                                                          |
| `sessionLifetime`     | `30 days`                   | How long a session lasts after it is issued                                                                               | Positive                                                                          |
| `idleLifetime`        | `7 days`                    | How long a session lasts after its last authentication, up to its lifetime                                                | Positive                                                                          |
| `exchangeLifetime`    | `15 minutes`                | How long an exchange lasts after it starts, and an enrolment that no exchange finishes                                    | Positive                                                                          |
| `codeLifetime`        | `10 minutes`                | How long the code and the passkey challenge of a stage last after it starts                                               | Positive                                                                          |
| `maxAnswers`          | `5`                         | The most wrong answers that an exchange takes                                                                             | At least 1                                                                        |
| `argon2Options`       | `new Argon2Options()`       | The cost of password hashes                                                                                               | Not null                                                                          |

The keys that `macKey`, `encryptionKey` and `accessTokenKey` name are resources of `pyrite.dev/Key`. Create them with
`CreateKey` after the first declaration: the key of `macKey` with `HMAC_SHA256`, the key of `encryptionKey` with
`AES256_GCM`, and the key of `accessTokenKey` with `ES256`, `EDDSA` or `RS256`, so that services verify access tokens
with its public key.

## RelyingPartyOptions

| Option    | Default | Sets                                                                                             |
| --------- | ------- | ------------------------------------------------------------------------------------------------ |
| `id`      | None    | The WebAuthn relying party id, your domain, such as `acme.dev`                                   |
| `name`    | None    | The name that authenticators show, such as `Acme`                                                |
| `origins` | Empty   | The origins that passkey answers may come from, such as `https://acme.dev`; `addOrigin` adds one |

## ProviderOptions

| Option        | Default | Sets                                                                                                               |
| ------------- | ------- | ------------------------------------------------------------------------------------------------------------------ |
| `name`        | None    | The name that answers and linked factors use, such as `google`; matches `[a-z][a-z0-9-]{0,62}`                     |
| `auth`        | None    | The Vert.x provider, such as `GoogleAuth.discover(...)` or `GithubAuth.create(...)`, with the client id and secret |
| `redirectUri` | None    | The callback of your application that the provider sends the person back to                                        |
| `scopes`      | Empty   | The scopes to ask for, such as `openid` and `email`; `addScope` adds one                                           |

An OpenID Connect provider needs the scope `openid`, and discovery, so that Vert.x checks its ID tokens. See
[Sign in with a provider](../accounts/sign-in-with-a-provider.md).

## Argon2Options

Pyrite hashes passwords with Argon2id. The defaults are the second recommended option of RFC 9106.

| Option        | Default | Sets                       | Limits                                         |
| ------------- | ------- | -------------------------- | ---------------------------------------------- |
| `memory`      | `65536` | The memory, in KiB         | At least 8 per lane                            |
| `iterations`  | `3`     | The passes over the memory | At least 1                                     |
| `parallelism` | `4`     | The lanes                  | At least 1, with at least 8 KiB of memory each |

Argon2 gives each lane at least 8 KiB of memory, so `setMemory` and `setParallelism` each refuse a value that leaves
less. To lower the memory below that of the lanes before, set the lanes first:
`new Argon2Options().setParallelism(1).setMemory(19_456)`.

## Related

- [Serve the services](serve-the-services.md)
- [About keys and versions](../keys/about-keys-and-versions.md)
- [About accounts and exchanges](../accounts/about-accounts-and-exchanges.md)
- [Troubleshooting deployment](troubleshooting-deployment.md)
