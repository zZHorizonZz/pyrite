# Pyrite

Pyrite is the identity and access layer of a Vert.x application. Your application declares its types of resources
and their relations, keeps the resources in its own tables, and tells Pyrite when one is created and when it is gone.
Pyrite keeps the grants in OpenFGA, and answers who can do what.

```java
EventBusGrpcServer server = EventBusGrpcServer.server(vertx).await();
Pyrite.pyrite(vertx, pool, new PostgresDriver(), openFga, new PyriteOptions()).bind(server);

AccessGrpcClient access = AccessGrpcClient.create(EventBusGrpcClient.client(vertx).await());
access.createObject(CreateObjectRequest.newBuilder().setSubject("user:anne").setName("projects/ledger")
    .setParent("organizations/acme").build()).await();
boolean allowed = access.check(CheckRequest.newBuilder().setSubject("user:bob").setObject("projects/ledger")
    .setPermission("viewer").build()).await().getAllowed();
```

## When to use Pyrite

Use Pyrite when your application has resources that people share, and each call must answer who can do what:

- **Hierarchies:** organizations hold projects, and access granted on an organization reaches its projects.
- **Sharing:** owners grant relations to people, to groups and to everyone, with conditions such as an expiry time.
- **Roles that users define:** administrators create roles and bind them to people at runtime.
- **Accounts:** people sign in with passwords, passkeys, TOTP codes, one-time codes, recovery codes and providers
  such as Google or GitHub, and keep
  their sessions with short-lived access tokens and refresh tokens that work once.
- **Keys:** services encrypt data and sign tokens with keys whose versions rotate.

Pyrite trusts the subject that each request names. Put it behind the code that verifies a credential, such as a
gateway or a verticle that authenticates the session. Do not serve it to clients that you do not control.

## Features

- **Declared types.** A declaration names each type, its parents and its relations, as protobuf messages. For more
  information, refer to [About declarations](docs/declarations/about-declarations.md).
- **Known resources.** Your application keeps its resources. It makes each one known to Pyrite when it creates it,
  and forgets it when it is gone. For more information, refer to [About resources](docs/access/about-resources.md).
- **Checked calls.** Every call checks the subject's permission. A list returns only the resources that the subject
  has the permission on. For more information, refer to
  [About subjects and permissions](docs/access/about-subjects-and-permissions.md).
- **Keys.** Encryption, MAC and signing keys, whose versions rotate on a schedule. For more information, refer to
  [About keys and versions](docs/keys/about-keys-and-versions.md).
- **Accounts and exchanges.** Sign-in, verification, recovery and enrolment, as exchanges that your declaration defines.
  People sign in with the kinds of identifiers that you declare, such as an email address or an employee id. For more
  information, refer to [About accounts and exchanges](docs/accounts/about-accounts-and-exchanges.md).
- **Handlers.** Your code runs at the moments of an exchange, can stop it, sends the codes, and hears of the accounts
  and sessions that Pyrite creates and ends. For more information, refer to
  [Intercept exchanges](docs/accounts/intercept-exchanges.md).
- **Your database.** PostgreSQL, MariaDB, SQL Server or SQLite. The JAR contains the migrations of Pyrite's tables,
  whose foreign keys, defaults and triggers keep the rows consistent, and the database is the clock of every time
  that Pyrite stores. For more information, refer to [Create the tables](docs/deployment/create-the-tables.md).

## Services

Pyrite serves four gRPC services. `Pyrite.pyrite` makes them on one database, and `bind` adds all four to a Vert.x
gRPC server or event bus server; each method below gives one, to add alone.

| Service                                   | Method                    | Does                                                                             |
| ----------------------------------------- | ------------------------- | -------------------------------------------------------------------------------- |
| `pyrite.schema.v1.Schema`                 | `pyrite.schema()`         | Writes and reads the declaration                                                 |
| `pyrite.access.v1.Access`                 | `pyrite.access()`         | Makes resources known, grants and revokes relations, checks and lists            |
| `pyrite.crypto.v1.Cryptography`           | `pyrite.cryptography()`   | Creates and rotates keys, and encrypts, decrypts, signs and verifies             |
| `pyrite.authentication.v1.Authentication` | `pyrite.authentication()` | Keeps accounts, and runs sign-in, verification, recovery and enrolment exchanges |

## Get started

To serve Pyrite and check your first permission, refer to [Get started](docs/get-started.md). Pyrite requires Java 25
and Vert.x 5. It is the Java module `dev.horizon.pyrite`. The JAR contains cel-java, RE2/J and ANTLR under
`dev.horizon.pyrite.shaded`, so your application adds no CEL module.

<!-- x-release-please-start-version -->
```xml
<dependency>
  <groupId>dev.horizon</groupId>
  <artifactId>pyrite</artifactId>
  <version>0.3.2-SNAPSHOT</version>
</dependency>
```
<!-- x-release-please-end -->

## Documentation

- **Get started:** [Get started](docs/get-started.md), and [the areas of the documentation](docs/README.md)
- **Declarations:** [About declarations](docs/declarations/about-declarations.md),
  [Declare a type](docs/declarations/declare-a-type.md),
  [Let users define roles](docs/declarations/let-users-define-roles.md),
  [Declaration reference](docs/declarations/declaration-reference.md),
  [Pyrite's types](docs/declarations/pyrite-types.md),
  [Troubleshooting declarations](docs/declarations/troubleshooting-declarations.md)
- **Access:** [About resources](docs/access/about-resources.md),
  [About subjects and permissions](docs/access/about-subjects-and-permissions.md),
  [Purge deleted resources](docs/access/purge-deleted-resources.md),
  [Troubleshooting access](docs/access/troubleshooting-access.md)
- **Accounts:** [About accounts and exchanges](docs/accounts/about-accounts-and-exchanges.md), [Sign in with a
  password](docs/accounts/sign-in-with-a-password.md), [Add a second factor](docs/accounts/add-a-second-factor.md), [Add
  a passkey](docs/accounts/add-a-passkey.md), [Verify an email address](docs/accounts/verify-an-email-address.md),
  [Recover an account](docs/accounts/recover-an-account.md), [Intercept
  exchanges](docs/accounts/intercept-exchanges.md), [Troubleshooting
  accounts](docs/accounts/troubleshooting-accounts.md)
- **Keys:** [About keys and versions](docs/keys/about-keys-and-versions.md), [Encrypt data](docs/keys/encrypt-data.md),
  [Sign and verify tokens](docs/keys/sign-and-verify-tokens.md), [Rotate a key](docs/keys/rotate-a-key.md),
  [Troubleshooting keys](docs/keys/troubleshooting-keys.md)
- **Deployment:** [Create the tables](docs/deployment/create-the-tables.md),
  [Serve the services](docs/deployment/serve-the-services.md), [Options](docs/deployment/options.md),
  [Tables](docs/deployment/tables.md), [Status codes](docs/deployment/status-codes.md),
  [Troubleshooting deployment](docs/deployment/troubleshooting-deployment.md)

## Run the tests

Most of Pyrite's tests are conformance modules: scenarios that call Pyrite's services as a client would, each step tied
to a requirement of Pyrite's contract. The plans of modules cover sessions, exchanges, codes, passwords, identifiers and
accounts, second factors, passkeys, providers, handlers, access, declarations and keys. One plan checks Pyrite's checks
and lists against plain OpenFGA on the same grants.

Each module runs in every variant: on PostgreSQL, MariaDB and SQL Server in Docker through Testcontainers and on
SQLite in process, one database at a time; with the codes of exchanges in their responses or taken by an interceptor;
and over the event bus or gRPC over HTTP. A failure names the module, its variant, each step, and the requirement
that broke.

1. Start Docker.
2. Run the build:

   ```sh
   mvn verify
   ```

To run on one database, name it with `pyrite.test.database`: `postgres`, `mariadb`, `sqlserver` or `sqlite`. The
few unit tests run on SQLite unless the property names another. To test another server version, set its image with
a system property:

| Property                      | Default                                      |
| ----------------------------- | -------------------------------------------- |
| `pyrite.test.postgres.image`  | `postgres:17-alpine`                         |
| `pyrite.test.mariadb.image`   | `mariadb:13.0`                               |
| `pyrite.test.sqlserver.image` | `mcr.microsoft.com/mssql/server:2022-latest` |

## Resources

- [Spectrum](https://github.com/zZHorizonZz/spectrum), the OpenFGA service for Vert.x, which can hold the grants
- [OpenFGA](https://openfga.dev/), whose authorization models the declaration uses
- [Cinder](https://github.com/zZHorizonZz/cinder), which reads and writes the tables
- [Leadseal](https://github.com/zZHorizonZz/leadseal), which generates the migrations of Pyrite's tables
- [aquery](https://github.com/zZHorizonZz/aquery), whose [AIP-158](https://google.aip.dev/158) pages the lists of grants use
- [Tink](https://developers.google.com/tink), whose `Aead` encrypts the material of keys

## License

Apache License 2.0. For more information, refer to [LICENSE](LICENSE).
