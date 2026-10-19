# Get started

In this guide, you keep the keys of an application in PostgreSQL. You create a signing key, sign a session token
with it, and verify the token.

## Prerequisites

- Java 25
- A PostgreSQL 13 or later database
- [Tinkey](https://developers.google.com/tink/tinkey-overview) 1.12 or later, to create the root keyset
- Flyway, to create the tables

## 1. Create a root keyset

The root keyset encrypts every version that Pyrite stores in your database.

1. Create the keyset:

   ```sh
   tinkey create-keyset --key-template AES256_GCM --out root.json
   ```

2. Keep `root.json` out of your repository. Anyone who holds it can read every key.

## 2. Add the dependencies

Add Pyrite, the Cinder driver of your database, and its Vert.x client:

<!-- x-release-please-start-version -->
```xml
<dependency>
  <groupId>dev.horizon</groupId>
  <artifactId>pyrite</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```
<!-- x-release-please-end -->

```xml
<dependency>
  <groupId>dev.horizon</groupId>
  <artifactId>cinder-database-postgres</artifactId>
  <version>0.1.0</version>
</dependency>
<dependency>
  <groupId>io.vertx</groupId>
  <artifactId>vertx-pg-client</artifactId>
  <version>5.2.0</version>
</dependency>
```

On the module path, require Pyrite and the driver:

```java
module com.example.app {
  requires dev.horizon.pyrite;
  requires dev.horizon.cinder.postgres;
  requires io.vertx.sql.client.pg;
}
```

## 3. Create the tables

Run the migrations of Pyrite with their own history table:

```java
Flyway.configure()
    .dataSource("jdbc:postgresql://localhost:5432/app", "app", "secret")
    .locations("classpath:pyrite/migration/**/postgresql")
    .table("pyrite_schema_history")
    .baselineOnMigrate(true)
    .load()
    .migrate();
```

## 4. Create the keyring and a key

1. Read the root keyset as a Tink `Aead`:

   ```java
   AeadConfig.register();
   Aead root = TinkJsonProtoKeysetFormat.parseKeyset(Files.readString(Path.of("root.json")),
       InsecureSecretKeyAccess.get()).getPrimitive(RegistryConfiguration.get(), Aead.class);
   ```

2. Create the keyring over your database:

   ```java
   Vertx vertx = Vertx.vertx();
   Pool pool = PgBuilder.pool().connectingTo("postgresql://app:secret@localhost:5432/app").using(vertx).build();
   Keyring keyring = Keyring.create(vertx, DatabaseProvider.open(vertx, pool, new PostgresDriver(), root)
       .await(10, TimeUnit.SECONDS));
   ```

   The provider opens its own Cinder database on the pool, so it shares the connections of your application. The
   first time it opens on a database, it makes the vault key and stores it encrypted under the root key.

3. Create a signing key once. It gets a new version every 30 days:

   ```java
   SigningKey sessions = keyring.create("sessions",
       new Signing(Algorithm.ES256, Duration.ofDays(1), Duration.ofDays(7)),
       Rotation.every(Duration.ofDays(30))).await(10, TimeUnit.SECONDS);
   ```

   A new version is published one day before it signs. A retired version stays published for seven days.

## 5. Sign and verify a token

1. Sign a token:

   ```java
   String token = sessions.jwt(new JsonObject()
       .put("iss", "https://app.example")
       .put("aud", "app.example")
       .put("sub", "accounts/42")
       .put("exp", Instant.now().plus(Duration.ofHours(1)).getEpochSecond())).await(10, TimeUnit.SECONDS);
   ```

2. Verify it:

   ```java
   Verification verification = sessions.verify(token, new Expected("JWT", "https://app.example", "app.example"))
       .await(10, TimeUnit.SECONDS);
   ```

   `verification` is `Verified`, and `verified.claims().getString("sub")` is `accounts/42`. The same token checked
   with the audience `admin.example` is `Rejected` with the reason `AUDIENCE`.

3. Publish the keys:

   ```java
   JsonObject jwks = sessions.published().await(10, TimeUnit.SECONDS);
   ```

   `jwks` holds one JWK, with the id of the primary version as its `kid`. Serve it at your `jwks_uri`.

## Next steps

- [Keys and versions](concepts/keys-and-versions.md) explains purposes, rotation and the life of a version.
- [Providers](concepts/providers.md) explains how to keep keys in memory, a keyset file or a cloud KMS.
- [Problem types](reference/problem-types.md) lists every failure that a call can answer with.
