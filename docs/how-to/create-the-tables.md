# Create the tables

Pyrite reads and writes three sets of tables: its own, your types', and those of the OpenFGA server that holds the
grants. Create all three before you write a declaration.

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

## Create the tables of your types

Each declared type has a table that your migrations create. The table has the ten standard columns, and a column for
each of the type's own fields.

1. Create the table, with `uid` as its primary key and a unique index on `name`. On PostgreSQL:

   ```sql
   CREATE TABLE "acme_project" ("uid" varchar(36) NOT NULL, "name" varchar(255) COLLATE "C" NOT NULL,
     "parent" varchar(255), "display_name" varchar(255), "labels" jsonb NOT NULL,
     "revision" bigint NOT NULL, "create_time" timestamptz NOT NULL, "update_time" timestamptz NOT NULL,
     "delete_time" timestamptz, "purge_time" timestamptz, "billing_account" varchar(64),
     CONSTRAINT "pk_acme_project" PRIMARY KEY ("uid"));
   CREATE UNIQUE INDEX "ux_acme_project_name" ON "acme_project" ("name");
   ```

2. Index `parent` for a type with parents. Queries read the rows beneath a set of parents:

   ```sql
   CREATE INDEX "ix_acme_project_parent" ON "acme_project" ("parent");
   ```

3. Declare the type with the same table and fields. For more information, refer to
   [Declare a type](declare-a-type.md).

For the column of each kind of field, refer to [Tables](../reference/tables.md#columns-of-your-tables).

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
