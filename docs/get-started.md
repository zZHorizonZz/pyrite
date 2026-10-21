# Get started

In this guide, you store the keys of an application in PostgreSQL. You create a signing key, sign a session token
with it, verify the token, and publish the public keys.

## Prerequisites

- Java 25
- PostgreSQL 13 or later
- [Tinkey](https://developers.google.com/tink/tinkey-overview) 1.12 or later
- Flyway

## 1. Create a root keyset

Pyrite encrypts the material of every version with a vault key, and it encrypts the vault key with your root key.

1. Create the root keyset:

   ```sh
   tinkey create-keyset --key-template AES256_GCM --out root.json
   ```

2. Store `root.json` outside your repository. Anyone who has it and a copy of your database can read every key.

## 2. Add the dependencies

Add the database provider, the Cinder driver for PostgreSQL, and the Vert.x PostgreSQL client:

<!-- x-release-please-start-version -->
```xml
<dependency>
  <groupId>dev.horizon</groupId>
  <artifactId>pyrite-database</artifactId>
  <version>0.3.0</version>
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

If your application is a Java module, require the provider and the driver:

```java
module com.example.app {
  requires dev.horizon.pyrite.database;
  requires dev.horizon.cinder.postgres;
  requires io.vertx.sql.client.pg;
}
```

## 3. Create the tables

Run the migrations of Pyrite. Give them their own history table, so that they do not mix with the migrations of your
application:

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

1. Load the root keyset as a Tink `Aead`:

   ```java
   AeadConfig.register();
   Aead root = TinkJsonProtoKeysetFormat.parseKeyset(Files.readString(Path.of("root.json")),
       InsecureSecretKeyAccess.get()).getPrimitive(RegistryConfiguration.get(), Aead.class);
   ```

2. Build a Cinder database on your connection pool, with the protobuf extension. Pyrite's tables are protobuf
   messages, so the provider requires it:

   ```java
   Vertx vertx = Vertx.vertx();
   Pool pool = PgBuilder.pool().connectingTo("postgresql://app:secret@localhost:5432/app").using(vertx).build();
   Database database = Database.builder(pool, new PostgresDriver()).extension(new ProtobufExtension()).build();
   ```

3. Open the provider on the database, and create the keyring:

   ```java
   Keyring keyring = Keyring.create(vertx, DatabaseProvider.open(vertx, database, root).await(10, TimeUnit.SECONDS));
   ```

   The first time the provider opens on a database, it creates the vault key.

4. Create a signing key. Do this once, when you set up the application:

   ```java
   SigningKey sessions = keyring.create("sessions", new Signing(Algorithm.ES256),
       Rotation.every(Duration.ofDays(30)),
       Validity.of(Duration.ofDays(31)).pending(Duration.ofHours(1))).await(10, TimeUnit.SECONDS);
   ```

   The key gets a new version every 30 days. Each new version is published for an hour before it signs, and it
   expires 31 days after it is created. A token signed just before a rotation can therefore be verified for about one
   more day.

## 5. Sign and verify a token

1. Sign a token:

   ```java
   String token = sessions.jwt(new JsonObject()
       .put("iss", "https://app.example")
       .put("aud", "app.example")
       .put("sub", "accounts/42")
       .put("exp", Instant.now().plus(Duration.ofHours(1)).getEpochSecond())).await(10, TimeUnit.SECONDS);
   ```

2. Verify the token:

   ```java
   Verification verification = sessions.verify(token, new Expected("JWT", "https://app.example", "app.example"))
       .await(10, TimeUnit.SECONDS);
   ```

   The result is `Verified`, and `verified.claims().getString("sub")` returns `accounts/42`. If you verify the same
   token with the audience `admin.example`, the result is `Rejected` with the reason `AUDIENCE`.

3. Get the JWK set:

   ```java
   JsonObject jwks = sessions.published().await(10, TimeUnit.SECONDS);
   ```

   The set contains one key. Its `kid` is the id of the primary version. Serve the set at your `jwks_uri`.

## Next steps

- Read [Keys and versions](concepts/keys-and-versions.md) to learn about purposes, rotation and validity.
- Read [Providers](concepts/providers.md) to learn how the database provider protects keys.
- Refer to [Problem types](reference/problem-types.md) for the failures that a call can return.
