# Pyrite

Pyrite is the data layer of a Vert.x application, with identity and access built in. Your application declares its
types of resources and their relations. Pyrite stores the resources in your tables and keeps their grants in OpenFGA.
It checks every call against those grants.

```java
EventBusGrpcServer server = EventBusGrpcServer.server(vertx).await();
server.addService(DataService.create(vertx, pool, new PostgresDriver(), openFga));

DataGrpcClient data = DataGrpcClient.create(EventBusGrpcClient.client(vertx).await());
Resource ledger = data.getResource(GetResourceRequest.newBuilder()
    .setSubject("user:anne").setName("projects/ledger").build()).await();
```

## When to use Pyrite

Use Pyrite when your application stores resources that people share, and each call must answer who can do what:

- **Hierarchies:** organizations hold projects, and access granted on an organization reaches its projects.
- **Sharing:** owners grant relations to people, to groups and to everyone, with conditions such as an expiry time.
- **Roles that users define:** administrators create roles and bind them to people at runtime.
- **Accounts:** people sign in with passwords, one-time codes and recovery codes, and keep their sessions.
- **Keys:** services encrypt data and sign tokens with keys whose versions rotate.

Pyrite trusts the subject that each request names. Put it behind the code that verifies a credential, such as a
gateway or a verticle that authenticates the session. Do not serve it to clients that you do not control.

## Features

- **Declared types.** A declaration names each type, its parents, its table, its fields and its relations, as
  protobuf messages. For more information, refer to [Declarations](docs/concepts/declarations.md).
- **Checked calls.** Every call checks the subject's permission. A query returns only the resources that the subject
  may see. For more information, refer to [Subjects and permissions](docs/concepts/subjects-and-permissions.md).
- **Resources with a lifecycle.** Resources have revisions and move between parents. A deleted resource stays for a
  retention period before Pyrite purges it. For more information, refer to [Resources](docs/concepts/resources.md).
- **Keys.** Encryption, MAC and signing keys, whose versions rotate on a schedule. For more information, refer to
  [Keys and versions](docs/concepts/keys-and-versions.md).
- **Accounts and flows.** Sign-in, verification, recovery and enrolment, as flows that your declaration defines. For
  more information, refer to [Accounts and flows](docs/concepts/accounts-and-flows.md).
- **Your database.** PostgreSQL, MariaDB, SQL Server or SQLite. The JAR contains the migrations of Pyrite's tables.
  For more information, refer to [Create the tables](docs/how-to/create-the-tables.md).

## Services

Pyrite serves five gRPC services. Each one is a Vert.x gRPC service with its own `create` method, as Spectrum's
`OpenFgaService` is.

| Service                                   | Java interface          | Does                                                                 |
| ----------------------------------------- | ----------------------- | -------------------------------------------------------------------- |
| `pyrite.schema.v1.Schema`                 | `SchemaService`         | Writes and reads the declaration                                     |
| `pyrite.data.v1.Data`                     | `DataService`           | Creates, gets, queries, updates, moves, deletes and purges resources |
| `pyrite.access.v1.Access`                 | `AccessService`         | Grants and revokes relations, checks permissions, and lists grants   |
| `pyrite.crypto.v1.Cryptography`           | `CryptographyService`   | Encrypts, decrypts, signs and verifies with keys                     |
| `pyrite.authentication.v1.Authentication` | `AuthenticationService` | Runs sign-in, verification, recovery and enrolment flows             |

## Get started

To serve Pyrite and check your first permission, refer to [Get started](docs/get-started.md). Pyrite requires Java 25
and Vert.x 5. It is the Java module `dev.horizon.pyrite`.

<!-- x-release-please-start-version -->
```xml
<dependency>
  <groupId>dev.horizon</groupId>
  <artifactId>pyrite</artifactId>
  <version>0.1.1-SNAPSHOT</version>
</dependency>
```
<!-- x-release-please-end -->

## Documentation

- **Get started:** [Get started](docs/get-started.md)
- **Concepts:** [Declarations](docs/concepts/declarations.md),
  [Subjects and permissions](docs/concepts/subjects-and-permissions.md), [Resources](docs/concepts/resources.md),
  [Keys and versions](docs/concepts/keys-and-versions.md), [Accounts and flows](docs/concepts/accounts-and-flows.md)
- **How-to:** [Create the tables](docs/how-to/create-the-tables.md),
  [Serve the services](docs/how-to/serve-the-services.md), [Declare a type](docs/how-to/declare-a-type.md),
  [Let users define roles](docs/how-to/let-users-define-roles.md),
  [Purge deleted resources](docs/how-to/purge-deleted-resources.md), [Encrypt data](docs/how-to/encrypt-data.md),
  [Sign and verify tokens](docs/how-to/sign-and-verify-tokens.md), [Rotate a key](docs/how-to/rotate-a-key.md),
  [Sign in with a password](docs/how-to/sign-in-with-a-password.md),
  [Add a second factor](docs/how-to/add-a-second-factor.md),
  [Verify an email address](docs/how-to/verify-an-email-address.md),
  [Recover an account](docs/how-to/recover-an-account.md)
- **Reference:** [Declaration](docs/reference/declaration.md), [Pyrite's types](docs/reference/pyrite-types.md),
  [Options](docs/reference/options.md), [Tables](docs/reference/tables.md),
  [Status codes](docs/reference/status-codes.md)
- **Troubleshooting:** [Troubleshooting Pyrite](docs/troubleshooting.md)

## Run the tests

The suites run against one database at a time: PostgreSQL, MariaDB or SQL Server in Docker through
Testcontainers, or SQLite in process. Each suite serves Pyrite and Spectrum over the event bus. One suite checks Pyrite's checks, queries and
lists against plain OpenFGA on the same grants.

1. Start Docker.
2. Run the build, here on PostgreSQL:

   ```sh
   mvn verify -Dpyrite.test.database=postgres
   ```

The tests run on SQLite unless `pyrite.test.database` names another database: `postgres`, `mariadb`,
`sqlserver` or `sqlite`. CI runs all four. To test another server version, set its image with a system property:

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
- [aquery](https://github.com/zZHorizonZz/aquery), which parses the filters of [AIP-160](https://google.aip.dev/160)
- [Tink](https://developers.google.com/tink), whose `Aead` encrypts the material of keys

## License

Apache License 2.0. For more information, refer to [LICENSE](LICENSE).
