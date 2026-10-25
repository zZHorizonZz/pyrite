# Create the tables

Pyrite reads and writes two sets of tables: its own, and those of the OpenFGA server that holds the grants. Create
both before you write a declaration.

Pyrite does not store your application's resources, so your types need no tables in Pyrite. Your application keeps
its resources in its own tables, with its own migrations.

## Before you begin

- A PostgreSQL, MariaDB, SQL Server or SQLite database.
- Flyway, or another tool that runs SQL migrations.

## Create Pyrite's tables

The JAR contains Pyrite's migrations under `pyrite/migration`, one directory per migration and one subdirectory per
database: `postgresql`, `mariadb`, `sqlserver` and `sqlite`.

1. Run the migrations of your database, with their own history table:

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

The tables have the prefix `pyrite_`. For their columns, refer to [Tables](../reference/tables.md).

## Create the tables of OpenFGA

Pyrite keeps its grants in an OpenFGA server, through a client of OpenFGA's gRPC API. With Spectrum, the grants live
in the same database. Run Spectrum's migrations the same way:

```java
Flyway.configure()
    .dataSource("jdbc:postgresql://localhost:5432/app", "app", "secret")
    .locations("classpath:spectrum/migration/**/postgresql")
    .table("spectrum_schema_history")
    .baselineOnMigrate(true)
    .load()
    .migrate();
```

## Related resources

- [Tables](../reference/tables.md)
- [Serve the services](serve-the-services.md)
- [Spectrum: Create the tables](https://github.com/zZHorizonZz/spectrum/blob/main/docs/how-to/create-the-tables.md)
