# Pyrite

Pyrite manages the keys of a Vert.x application. It encrypts secrets that you store, computes MACs of credentials
that you only compare, and signs JWTs. Each key has versions that rotate on a schedule and expire after a lifetime
that you set. The keys are stored by a provider, such as your application's database.

```java
Database database = Database.builder(pool, new PostgresDriver()).extension(new ProtobufExtension()).build();
Keyring keyring = Keyring.create(vertx, DatabaseProvider.open(vertx, database, root).await(10, TimeUnit.SECONDS));

EncryptionKey seeds = keyring.encryption("totp-seeds").await(10, TimeUnit.SECONDS);
byte[] sealed = seeds.encrypt(seed, "accounts/42").await(10, TimeUnit.SECONDS);

SigningKey sessions = keyring.signing("sessions").await(10, TimeUnit.SECONDS);
String token = sessions.jwt(new JsonObject().put("sub", "accounts/42").put("exp", expires)).await(10, TimeUnit.SECONDS);
switch (sessions.verify(token, new Expected("JWT", "https://app.example", "app.example")).await(10, TimeUnit.SECONDS)) {
  case Verified verified -> signIn(verified.claims());
  case Rejected rejected -> refuse(rejected.reason());
}
```

## Use cases

Use an encryption key for secrets that your application reads back, such as TOTP seeds. Each ciphertext is bound to
a context, such as the account it belongs to, and decrypts under that context only.

Use a MAC key for credentials that your application only compares, such as one-time codes, recovery codes or refresh
tokens. Store the MAC instead of the credential.

Use a signing key for tokens that your application issues, such as sessions or ID tokens. Verifiers fetch the public
keys as a JWK set.

Use a signing key with exported or uploaded versions for a machine, such as a service account. The machine holds the
private key, and Pyrite verifies the JWTs that it signs.

## Install

Pyrite requires Java 25 and Vert.x 5. Add the artifact of the provider that you use. It depends on the rest of
Pyrite.

<!-- x-release-please-start-version -->
```xml
<dependency>
  <groupId>dev.horizon</groupId>
  <artifactId>pyrite-database</artifactId>
  <version>0.2.1</version>
</dependency>
```
<!-- x-release-please-end -->

| Artifact          | Java module                   | Contents                                                                                  |
| ----------------- | ----------------------------- | ----------------------------------------------------------------------------------------- |
| `pyrite-common`   | `dev.horizon.pyrite.common`   | `Subject`, `Revision` and the problem exceptions, shared by the libraries built on Pyrite |
| `pyrite-api`      | `dev.horizon.pyrite.api`      | the interfaces of the keyring, keys and versions, and the provider interface              |
| `pyrite-internal` | `dev.horizon.pyrite.internal` | the keyring, and the cryptography of the providers that run in the process                |
| `pyrite-database` | `dev.horizon.pyrite.database` | the database provider and its migrations                                                  |
| `pyrite-memory`   | `dev.horizon.pyrite.memory`   | the memory provider                                                                       |

Your code uses only `pyrite-api` and the class of its provider. Do not depend on `pyrite-internal` directly.

Providers for Google Cloud KMS, AWS KMS and Tink keyset files are planned.

## Documentation

- [Get started](docs/get-started.md)
- [Keys and versions](docs/concepts/keys-and-versions.md)
- [Providers](docs/concepts/providers.md)
- [Problem types](docs/reference/problem-types.md)

## Standards

- [RFC 7515](https://www.rfc-editor.org/rfc/rfc7515) (JWS), [RFC 7517](https://www.rfc-editor.org/rfc/rfc7517) (JWK),
  [RFC 7518](https://www.rfc-editor.org/rfc/rfc7518) (JWA) and [RFC 7519](https://www.rfc-editor.org/rfc/rfc7519) (JWT)
- [RFC 8037](https://www.rfc-editor.org/rfc/rfc8037), for EdDSA keys in JWK sets
- [RFC 7523](https://www.rfc-editor.org/rfc/rfc7523), for the JWTs that machines sign as client assertions
- [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457), for the problem details of failures

## Built with

- [Nimbus JOSE + JWT](https://connect2id.com/products/nimbus-jose-jwt) parses JWTs and writes JWK sets.
- [Tink](https://developers.google.com/tink) decrypts the vault key with your root key.
- [Caffeine](https://github.com/ben-manes/caffeine) caches the decrypted material of versions.
- [Cinder](https://github.com/zZHorizonZz/cinder) reads and writes the tables of the database provider.
- [Leadseal](https://github.com/zZHorizonZz/leadseal) generates their migrations.

The model of keyrings, keys and versions comes from [Cloud KMS](https://docs.cloud.google.com/kms/docs/resource-hierarchy).

## License

Apache License 2.0. For more information, refer to [LICENSE](LICENSE).
