# Pyrite

Pyrite keeps the keys of a Vert.x application. Its keys encrypt what you store and fingerprint what you only compare.
They also sign tokens and publish themselves as a JWK set. Every key has a purpose and versions that rotate. A
provider holds them: your own database, memory, a keyset file or a cloud KMS.

```java
Keyring keyring = Keyring.create(vertx, DatabaseProvider.open(vertx, pool, new PostgresDriver(), root)
    .await(10, TimeUnit.SECONDS));

EncryptionKey seeds = keyring.encryption("totp-seeds").await(10, TimeUnit.SECONDS);
byte[] sealed = seeds.encrypt(seed, "accounts/42").await(10, TimeUnit.SECONDS);

SigningKey sessions = keyring.signing("sessions").await(10, TimeUnit.SECONDS);
String token = sessions.jwt(new JsonObject().put("sub", "accounts/42").put("exp", expires)).await(10, TimeUnit.SECONDS);
switch (sessions.verify(token, new Expected("JWT", "https://app.example", "app.example")).await(10, TimeUnit.SECONDS)) {
  case Verified verified -> signIn(verified.claims());
  case Rejected rejected -> refuse(rejected.reason());
}
```

## When to use Pyrite

- **You store secrets that you read back**, such as TOTP seeds. Each one decrypts only for what it belongs to.
- **You store credentials that you only compare**, such as one-time codes or refresh tokens. You keep a
  fingerprint instead of the credential.
- **You issue tokens**, such as session cookies or ID tokens. Their keys rotate, and verifiers fetch them as a JWK set.
- **You give machines keys**, such as service accounts. You export the private half once and verify what they sign.

## Features

- **One model for every key.** Encryption, MAC and signing keys all have versions, a primary and a rotation.
- **Rotation without migrations.** What a version makes names that version, so older versions keep working.
- **Signing keys that publish ahead.** A new version is published before it signs anything.
- **Exported and uploaded versions.** A machine can hold the private half, and Pyrite keeps the public half.
- **Verification as a result.** `verify` answers `Verified` or `Rejected` with the reason.
- **Providers for your database, memory, a keyset file, Cloud KMS and AWS KMS.** Every call stays the same.
- **Problem details.** Every failure is a problem details object of RFC 9457.

## Install

Pyrite requires Java 25 and Vert.x 5. It is the Java module `dev.horizon.pyrite`.

<!-- x-release-please-start-version -->
```xml
<dependency>
  <groupId>dev.horizon</groupId>
  <artifactId>pyrite</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```
<!-- x-release-please-end -->

| Artifact | Holds |
| --- | --- |
| `pyrite-common` | `Subject`, `Revision` and the problem exceptions that the libraries built on Pyrite share |
| `pyrite-api` | the keyring, its keys and versions, and the provider interface |
| `pyrite` | the keyring, the database, memory and keyset file providers, and the migrations |
| `pyrite-gcpkms` | the Google Cloud KMS provider |
| `pyrite-awskms` | the AWS KMS provider |

## Documentation

- **Get started:** [Get started](docs/get-started.md)
- **Concepts:** [Keys and versions](docs/concepts/keys-and-versions.md), [Providers](docs/concepts/providers.md)
- **Reference:** [Problem types](docs/reference/problem-types.md)

## Standards

- [RFC 7515](https://www.rfc-editor.org/rfc/rfc7515) JWS and [RFC 7517](https://www.rfc-editor.org/rfc/rfc7517) JWK
- [RFC 7518](https://www.rfc-editor.org/rfc/rfc7518) JWA and [RFC 7519](https://www.rfc-editor.org/rfc/rfc7519) JWT
- [RFC 8037](https://www.rfc-editor.org/rfc/rfc8037), EdDSA in JOSE
- [RFC 7523](https://www.rfc-editor.org/rfc/rfc7523), the JWTs that machines sign with exported versions
- [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457), problem details

## Resources

- [Cloud KMS](https://cloud.google.com/kms/docs/resource-hierarchy), whose model Pyrite follows
- [AWS KMS](https://docs.aws.amazon.com/kms/), whose model Pyrite also follows
- [Tink](https://developers.google.com/tink), which encrypts and fingerprints in the process
- [Nimbus JOSE + JWT](https://connect2id.com/products/nimbus-jose-jwt), which writes and verifies JWTs and JWK sets
- [Bouncy Castle](https://www.bouncycastle.org/), which issues the certificate of each signing version
- [Cinder](https://github.com/zZHorizonZz/cinder), which reads and writes the tables
- [Leadseal](https://github.com/zZHorizonZz/leadseal), which generates the migrations

## License

Apache License 2.0. For more information, refer to [LICENSE](LICENSE).
