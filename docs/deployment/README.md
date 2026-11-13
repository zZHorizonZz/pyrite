# Deployment

Pyrite runs inside your Vert.x application: `Pyrite.pyrite(vertx, pool, driver, openFga, options)` makes its four gRPC
services over the tables of your database and an OpenFGA store, and `bind(server)` adds them to a gRPC server or an
event bus server. Every Pyrite on the same database serves the same declaration, keys and accounts.

## Features

**Your database.** PostgreSQL, MariaDB, SQL Server or SQLite. The JAR holds the migrations of Pyrite's tables, which
you run with Flyway, together with the migrations of Spectrum for the grants. See
[Create the tables](create-the-tables.md).

**Two transports.** Serve the services over the event bus to the verticles of your application, or over gRPC to
callers that you control, all four at once or one by one. See [Serve the services](serve-the-services.md).

**Options.** Limits of pages and reads, the root key of keys, and the keys, lifetimes and relying party of accounts,
each with a default and limits. See [Options](options.md).

**Tables that keep themselves consistent.** Foreign keys, unique indexes, enum columns and triggers keep the rows
right, and the database is the clock of every time that Pyrite stores. See [Tables](tables.md).

**Statuses.** A failed call ends with a gRPC status and a message that names the resource, the field or the rule. See
[Status codes](status-codes.md).

The errors of serving Pyrite are in [Troubleshooting deployment](troubleshooting-deployment.md).

## Related

- [Get started](../get-started.md)
- [Keys](../keys/README.md)
- [Accounts](../accounts/README.md)
- [Vert.x gRPC](https://vertx.io/docs/vertx-grpc/java/)
