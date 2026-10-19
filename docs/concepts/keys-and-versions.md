# Keys and versions

Pyrite follows the model of Cloud KMS and AWS KMS. A keyring holds keys, and a key holds versions. A key does one
thing, and its versions are the generations of the material that does it.

| Name | Is | Example |
| --- | --- | --- |
| `Keyring` | the keys of an application, held by one provider | the keys of your application in its database |
| `Key` | a named key with one purpose and a rotation | `totp-seeds`, `one-time-codes`, `sessions` |
| `Version` | one generation of a key, with its own state | the version that signed sessions until last month |

## Purposes

The purpose of a key decides its type and what it can do. A key keeps its purpose for its whole life.

| Purpose | Key type | Algorithm | Operations |
| --- | --- | --- | --- |
| `Encryption` | `EncryptionKey` | AES-256-GCM | `encrypt(plaintext, context)`, `decrypt(ciphertext, context)` |
| `Mac` | `MacKey` | HMAC-SHA256 | `fingerprint(text)`, `fingerprints(text)` |
| `Signing(algorithm, ahead, kept)` | `SigningKey` | RS256, ES256 or EdDSA | `jwt`, `sign`, `verify`, `published`, `certificates`, `export`, `upload` |

- **Encryption** authenticates a context with each plaintext, such as `accounts/42`. A ciphertext decrypts under its
  own context only.
- **MAC** writes a fingerprint to store in place of a credential. To look the credential up, ask for its
  fingerprints: there is one for each live version.
- **Signing** signs JWTs and bytes, and verifies them against the live versions of the key.

## The life of a version

Versions are numbered from `1` in their key, and each new version takes the next number. A key has exactly one
primary version. The primary encrypts, fingerprints or signs. Every live version decrypts, matches and verifies.

| State | Live | Means |
| --- | --- | --- |
| `Pending` | yes | a signing version that is published and becomes primary at a set time |
| `Primary` | yes | the version that encrypts, fingerprints or signs |
| `Live` | yes | a former primary, or an exported or uploaded version |
| `Disabled` | no | a version that does nothing until it is enabled again |
| `Destroyed` | no | a version whose material is gone, for good |

You cannot disable or destroy the primary version. Rotate the key first.

## Rotation

A rotation makes a new primary version: `Rotation.every(period)`, or `Rotation.never()`. `rotate()` makes one at once,
whatever the rotation says.

- **Encryption and MAC keys** keep every older version live. You disable or destroy an older version yourself, once
  nothing you stored needs it.
- **Signing keys** publish a new version for `ahead` before it signs. A retired version stays published for `kept`,
  then it is disabled. Set `kept` to at least the lifetime of your longest token.

What a version makes names its number: a ciphertext, a fingerprint, and the `kid` of a JWT. A rotation therefore
needs no migration of what you stored.

## Exported and uploaded versions

A signing key can hold versions whose private half someone else keeps. Such a version is live at once and never
primary. It verifies what its holder signs, such as a client assertion of RFC 7523.

- **Exported:** `export()` makes a version and returns its private key once, as PKCS#8. The provider keeps only the
  public half.
- **Uploaded:** `upload(publicKey)` adds a version for a public key that its holder made.

A key for a machine usually has `Rotation.never()`. The machine gets a new version when it asks for one.

## Concurrency

A key has a revision. Every new version and every change of a version moves it. A write that names an older revision
fails with `AbortedException`. Two processes over one database therefore never make two primary versions.

## What a key is not

- A key is not a password hash. Hash passwords with Argon2id, not with a MAC key.
- A key does not choose its algorithm or its rotation. You give both when you create it.
- A rotation does not destroy a version.
- A key does not add claims to a JWT. It signs the claims you give it.
- A key cannot change its purpose.
