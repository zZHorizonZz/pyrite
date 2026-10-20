# Keys and versions

A keyring contains keys, and each key contains versions. A version is one generation of a key's material. When a key
rotates, it gets a new version, and the older versions keep working until they expire.

| Object    | Description                                           | Example                                    |
| --------- | ----------------------------------------------------- | ------------------------------------------ |
| `Keyring` | the keys of an application, stored by one provider    | the keys in your application's database    |
| `Key`     | a named key with a purpose, a rotation and a validity | `totp-seeds`, `one-time-codes`, `sessions` |
| `Version` | one generation of a key, with its own state           | `sessions/versions/3`                      |

## Purposes

The purpose of a key sets its type and its operations. You choose it when you create the key, and you cannot change it.

| Purpose              | Key type        | Algorithm             | Operations                                               |
| -------------------- | --------------- | --------------------- | -------------------------------------------------------- |
| `Encryption`         | `EncryptionKey` | AES-256-GCM           | `encrypt`, `decrypt`                                     |
| `Mac`                | `MacKey`        | HMAC-SHA256           | `fingerprint`, `fingerprints`                            |
| `Signing(algorithm)` | `SigningKey`    | RS256, ES256 or EdDSA | `jwt`, `sign`, `verify`, `published`, `export`, `upload` |

`encrypt(plaintext, context)` takes a context, such as `accounts/42`. The context is authenticated but not stored.
`decrypt` fails unless it gets the same context.

Each encryption uses a random 96-bit nonce. With random nonces, AES-GCM must not encrypt more than 2^32 times under
one key ([NIST SP 800-38D, section 8.3](https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-38d.pdf)).
Pyrite does not count encryptions. The limit applies to each version, so rotate an encryption key long before its
primary version reaches it.

`fingerprint(text)` returns the MAC of the text under the primary version. Store it in place of the credential.
`fingerprints(text)` returns the MAC under every live version, so you can find a credential that was stored before a
rotation.

`jwt(claims)` signs the claims as you give them. Pyrite adds no claims. `verify(token, expected)` checks the
signature, `typ`, `iss`, `aud`, `exp` and `nbf`, and returns `Verified` or `Rejected` with a reason. It accepts only
the algorithm of the key ([RFC 8725, section 3.1](https://www.rfc-editor.org/rfc/rfc8725#section-3.1)), and it
requires the `typ` that you expect, so that one kind of JWT is not accepted as another
([RFC 8725, section 3.11](https://www.rfc-editor.org/rfc/rfc8725#section-3.11)).

## Use one key for one purpose

NIST requires that "a single key shall be used for only one purpose", because reusing a key for two processes can
weaken both, and a compromise of the key then affects both
([NIST SP 800-57 Part 1 Rev. 5, section 5.2](https://doi.org/10.6028/NIST.SP.800-57pt1r5)). The purpose of a Pyrite
key enforces this between encryption, MACs and signatures. Within a purpose, you must enforce it yourself:

- Do not sign JWTs and other bytes with the same key. `sign(data)` signs the bytes exactly as given, and a JWT is
  signed over its encoded header and claims, so a signature over bytes that someone else chose can be a valid JWT.
  RFC 8725 recommends different keys for different kinds of JWTs for the same reason
  ([RFC 8725, section 3.12](https://www.rfc-editor.org/rfc/rfc8725#section-3.12)).
- Do not export or upload versions of a key that also signs tokens for other principals. A verifier must check that
  the key that signed a JWT belongs to its issuer
  ([RFC 8725, section 3.8](https://www.rfc-editor.org/rfc/rfc8725#section-3.8)). The holder of an exported or uploaded
  version can sign any token that the key verifies, so create a key for each service account, and a separate key for
  your users' sessions.
- Do not share an encryption or MAC key between kinds of data. Create one key for TOTP seeds and another for one-time
  codes, so that a compromise of one key exposes one kind of data only.

## States

Versions are numbered from `1` within their key. A key has one primary version, which encrypts, computes MACs or
signs. Every live version decrypts, checks MACs and verifies.

| State       | Live | Description                                                                          |
| ----------- | ---- | ------------------------------------------------------------------------------------ |
| `Pending`   | yes  | a new version that becomes primary at a set time; a signing key already publishes it |
| `Primary`   | yes  | the version that encrypts, computes MACs or signs                                    |
| `Live`      | yes  | a former primary version, or an exported or uploaded version                         |
| `Disabled`  | no   | a version that you disabled; you can enable it again                                 |
| `Expired`   | no   | a version whose lifetime has ended; you cannot enable it again                       |
| `Destroyed` | no   | a version whose material was deleted                                                 |

You cannot disable or destroy the primary version. Rotate the key first.

## Rotation and validity

A key should be used for a limited time, its cryptoperiod. A limited cryptoperiod limits how much data one version
protects, and how much is exposed if that version is compromised
([NIST SP 800-57 Part 1 Rev. 5, section 5.3](https://doi.org/10.6028/NIST.SP.800-57pt1r5)). In Pyrite, the rotation
and the validity of a key set the cryptoperiod of its versions.

The rotation of a key sets when a new version becomes primary:

- `Rotation.every(period)` creates a new primary version after each period.
- `Rotation.never()` creates one only when you call `rotate()`.

The validity of a key sets how long each version works:

- `Validity.of(lifetime)` makes each version expire when the lifetime has passed since it was created.
- `Validity.unlimited()` makes versions work until you disable or destroy them.

Add `pending(duration)` to a validity to delay each new version before it becomes primary. A signing key publishes
the version during that time, so verifiers that cache your JWK set get the key before any token is signed with it.
Set it to the time for which your verifiers cache the set. OpenID Connect describes the same rotation: new keys are
added to the JWK set, and retired keys stay in it for a while
([OpenID Connect Core 1.0, section 10.1.1](https://openid.net/specs/openid-connect-core-1_0.html#RotateSigKeys)).

```java
SigningKey sessions = keyring.create("sessions", new Signing(Algorithm.ES256),
    Rotation.every(Duration.ofDays(30)),
    Validity.of(Duration.ofDays(31)).pending(Duration.ofHours(1))).await(10, TimeUnit.SECONDS);
```

Each version of `sessions` is pending for an hour and primary for 30 days. It then verifies for about one more day,
until it expires. Choose a lifetime that covers your longest token after a rotation.

Pyrite checks the rotation and the validity together:

- If a key rotates, its lifetime must be at least the rotation period plus the pending time. Otherwise the primary
  version would expire before the next one replaces it.
- If a key never rotates, its lifetime must be longer than the pending time.

If a check fails, `create`, `rotation` or `validity` fails with `InvalidArgumentException`.

If a key never rotates, its primary version can expire. The key then has no primary version until you call
`rotate()`. Until then, `encrypt`, `fingerprint`, `jwt` and `sign` fail with `FailedPreconditionException` and the
reason `PRIMARY`.

When you change the rotation or the validity of a key, the change applies to versions created after it. Existing
versions keep their expiry time.

Each ciphertext, fingerprint and JWT contains the number of the version that made it. After a rotation, Pyrite uses
that number to find the right version, so you do not need to migrate stored data.

## Exported and uploaded versions

A signing key can have versions whose private key is held by someone else, such as a machine:

- `export()` creates a version and returns its private key once, in PKCS#8. The provider stores only the public key.
- `upload(publicKey)` adds a version for a public key that the holder created.

Exported and uploaded versions are live as soon as they are created, and they never become primary. They verify
what their holder signs, such as client assertions of RFC 7523. They expire according to the validity of the key.

A key for a machine usually has `Rotation.never()` and a limited validity, such as `Validity.of(Duration.ofDays(90))`.
The machine requests a new version before its current one expires.

## Concurrent changes

Each key has a revision. Every new version, and every change to a version, increments it. A change that names an older
revision fails with `AbortedException`. Because of this, two processes that share a database cannot both create a
primary version.
