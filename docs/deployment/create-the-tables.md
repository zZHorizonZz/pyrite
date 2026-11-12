# Create the tables

Create Pyrite's tables, and the tables of the OpenFGA service that holds the grants, before Pyrite writes its first
declaration.

## Before you begin

- A PostgreSQL, MariaDB, SQL Server or SQLite database.
- Flyway, with the module of your database, such as `org.flywaydb:flyway-database-postgresql`, or another tool that
  runs SQL migrations.

Pyrite does not store your application's resources, so your types need no tables in Pyrite. Your application keeps
its resources in its own tables, with its own migrations.

## Create Pyrite's tables

The JAR holds Pyrite's migrations under `pyrite/migration`: one directory per migration, and in it one directory per
database, `postgresql`, `mariadb`, `sqlserver` and `sqlite`.

1. Run the migrations of your database, with a history table of their own:

   ```java
   Flyway.configure()
       .dataSource("jdbc:postgresql://localhost:5432/app", "app", "secret")
       .locations("classpath:pyrite/migration/**/postgresql")
       .table("pyrite_schema_history")
       .baselineOnMigrate(true)
       .load()
       .migrate();
   ```

2. Run them again after each upgrade of Pyrite, which can add migrations.

## Create the tables of OpenFGA

Pyrite keeps its grants in an OpenFGA service, through a client of OpenFGA's gRPC API. With Spectrum, which Pyrite
depends on, the grants live in the same database. Run Spectrum's migrations the same way, with their own history
table:

```java
Flyway.configure()
    .dataSource("jdbc:postgresql://localhost:5432/app", "app", "secret")
    .locations("classpath:spectrum/migration/**/postgresql")
    .table("spectrum_schema_history")
    .baselineOnMigrate(true)
    .load()
    .migrate();
```

With an OpenFGA server in place of Spectrum, skip this step: the server keeps its own tables.

## Verify

The database has the nine tables `pyrite_account`, `pyrite_declaration`, `pyrite_factor`, `pyrite_flow`,
`pyrite_identifier`, `pyrite_key`, `pyrite_key_version`, `pyrite_node` and `pyrite_session`, all empty, and
`pyrite_schema_history` lists each migration as a success. Without the tables, every call of Pyrite fails with
`INTERNAL`. See [Troubleshooting deployment](troubleshooting-deployment.md#internal-error).

## Related

- [Tables](tables.md)
- [Serve the services](serve-the-services.md)
- [Spectrum](https://github.com/zZHorizonZz/spectrum)
