# Get started

In this guide, you serve Pyrite on PostgreSQL over the Vert.x event bus. You declare organizations and their
projects, create one of each, grant a relation on the organization, and read the project as another user.

## Before you begin

- Java 25
- A PostgreSQL 13 or later database
- Flyway, to create the tables

## 1. Add the dependencies

Add Pyrite, the Cinder driver and Basepoint dialect of your database, its Vert.x client, and the Vert.x gRPC event
bus:

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
  <version>0.3.0</version>
</dependency>
<dependency>
  <groupId>dev.horizon</groupId>
  <artifactId>basepoint-dialect-postgres</artifactId>
  <version>0.3.0</version>
</dependency>
<dependency>
  <groupId>io.vertx</groupId>
  <artifactId>vertx-pg-client</artifactId>
  <version>5.2.1</version>
</dependency>
<dependency>
  <groupId>io.vertx</groupId>
  <artifactId>vertx-grpc-eventbus</artifactId>
  <version>5.2.1</version>
</dependency>
```

Pyrite brings Spectrum, which serves OpenFGA's API from the same database. Pyrite keeps its grants there.

## 2. Create the tables

1. Write the migration of your own tables. Each type of resource has a table with Pyrite's standard columns. Save it
   as `src/main/resources/db/migration/V1__acme.sql`:

   ```sql
   CREATE TABLE "acme_organization" ("uid" varchar(36) NOT NULL, "name" varchar(255) COLLATE "C" NOT NULL,
     "parent" varchar(255) NOT NULL, "display_name" varchar(255) NOT NULL, "labels" jsonb NOT NULL,
     "revision" bigint NOT NULL, "create_time" timestamptz NOT NULL, "update_time" timestamptz NOT NULL,
     "delete_time" timestamptz, "purge_time" timestamptz, CONSTRAINT "pk_acme_organization" PRIMARY KEY ("uid"));
   CREATE UNIQUE INDEX "ux_acme_organization_name" ON "acme_organization" ("name");

   CREATE TABLE "acme_project" ("uid" varchar(36) NOT NULL, "name" varchar(255) COLLATE "C" NOT NULL,
     "parent" varchar(255) NOT NULL, "display_name" varchar(255) NOT NULL, "labels" jsonb NOT NULL,
     "revision" bigint NOT NULL, "create_time" timestamptz NOT NULL, "update_time" timestamptz NOT NULL,
     "delete_time" timestamptz, "purge_time" timestamptz, CONSTRAINT "pk_acme_project" PRIMARY KEY ("uid"));
   CREATE UNIQUE INDEX "ux_acme_project_name" ON "acme_project" ("name");
   CREATE INDEX "ix_acme_project_parent" ON "acme_project" ("parent");
   ```

2. Run your migrations, Pyrite's and Spectrum's, each with its own history table:

   ```java
   String url = "jdbc:postgresql://localhost:5432/app";
   for (String[] migrations : new String[][] { { "db/migration", "acme" },
       { "pyrite/migration/**/postgresql", "pyrite" }, { "spectrum/migration/**/postgresql", "spectrum" } }) {
     Flyway.configure().dataSource(url, "app", "secret").locations("classpath:" + migrations[0])
         .table(migrations[1] + "_schema_history").baselineOnMigrate(true).load().migrate();
   }
   ```

For more information, refer to [Create the tables](how-to/create-the-tables.md).

## 3. Declare the types

Save the declaration as `declaration.textproto`. It is a `pyrite.schema.v1.Declaration` in protobuf text format.
A member of an organization views its projects, and the creator of each resource is granted a relation on it:

```textproto
# proto-message: pyrite.schema.v1.Declaration

types {
  name: "acme.dev/Organization"
  plural: "organizations"
  table: "acme_organization"
  fields { name: "name" kind: STRING }
  fields { name: "uid" kind: STRING }
  fields { name: "parent" kind: STRING }
  fields { name: "display_name" kind: STRING }
  fields { name: "labels" kind: MAP }
  fields { name: "revision" kind: INTEGER }
  fields { name: "create_time" kind: TIMESTAMP }
  fields { name: "update_time" kind: TIMESTAMP }
  fields { name: "delete_time" kind: TIMESTAMP }
  fields { name: "purge_time" kind: TIMESTAMP }
  definition {
    type: "organization"
    relations { key: "admin" value { this {} } }
    relations {
      key: "member"
      value { union { child { this {} } child { computed_userset { relation: "admin" } } } }
    }
    metadata {
      relations { key: "admin" value { directly_related_user_types { type: "user" } } }
      relations { key: "member" value { directly_related_user_types { type: "user" } } }
    }
  }
  permissions { read: "member" update: "admin" delete: "admin" grant: "admin" }
  grantable: ["admin", "member"]
  creator: "admin"
  retention { seconds: 2592000 }
}

types {
  name: "acme.dev/Project"
  plural: "projects"
  parents: "acme.dev/Organization"
  table: "acme_project"
  fields { name: "name" kind: STRING }
  fields { name: "uid" kind: STRING }
  fields { name: "parent" kind: STRING }
  fields { name: "display_name" kind: STRING }
  fields { name: "labels" kind: MAP }
  fields { name: "revision" kind: INTEGER }
  fields { name: "create_time" kind: TIMESTAMP }
  fields { name: "update_time" kind: TIMESTAMP }
  fields { name: "delete_time" kind: TIMESTAMP }
  fields { name: "purge_time" kind: TIMESTAMP }
  definition {
    type: "project"
    relations { key: "owner" value { this {} } }
    relations {
      key: "viewer"
      value {
        union {
          child { this {} }
          child { computed_userset { relation: "owner" } }
          child { tuple_to_userset { tupleset { relation: "parent" } computed_userset { relation: "member" } } }
        }
      }
    }
    metadata {
      relations { key: "owner" value { directly_related_user_types { type: "user" } } }
      relations { key: "viewer" value { directly_related_user_types { type: "user" } } }
    }
  }
  permissions { create: "admin" read: "viewer" update: "owner" delete: "owner" grant: "owner" }
  grantable: ["owner", "viewer"]
  creator: "owner"
  retention { seconds: 2592000 }
}
```

For each field and rule, refer to [Declaration](reference/declaration.md).

## 4. Serve the services

Create Spectrum's service and Pyrite's services on one pool, and add them to an event bus server. Give every service
of Pyrite the same options:

```java
Vertx vertx = Vertx.vertx();
Pool pool = PgBuilder.pool().connectingTo("postgresql://app:secret@localhost:5432/app").using(vertx).build();

EventBusGrpcServer server = EventBusGrpcServer.server(vertx).await();
EventBusGrpcClient client = EventBusGrpcClient.client(vertx).await();
server.addService(OpenFgaService.create(vertx, pool, new PostgresDialect()));
OpenFGAServiceGrpcClient openFga = OpenFGAServiceGrpcClient.create(client);

PostgresDriver driver = new PostgresDriver();
PyriteServiceOptions options = new PyriteServiceOptions();
server.addService(SchemaService.create(vertx, pool, driver, openFga, options));
server.addService(DataService.create(vertx, pool, driver, openFga, options));
server.addService(AccessService.create(vertx, pool, driver, openFga, options));
```

## 5. Write the declaration

Parse the file, and write it with the schema service:

```java
Declaration.Builder declaration = Declaration.newBuilder();
TextFormat.merge(Files.readString(Path.of("declaration.textproto")), declaration);

SchemaGrpcClient schema = SchemaGrpcClient.create(client);
schema.writeDeclaration(WriteDeclarationRequest.newBuilder().setDeclaration(declaration).build()).await();
```

Every service reads the latest declaration from then on.

## 6. Call the services

1. Create an organization as `user:anne`. Anne becomes its `admin`, the type's `creator` relation:

   ```java
   DataGrpcClient data = DataGrpcClient.create(client);
   data.createResource(CreateResourceRequest.newBuilder().setSubject("user:anne").setResourceId("acme")
       .setResource(Resource.newBuilder().setType("acme.dev/Organization")).build()).await();
   ```

2. Create a project in it:

   ```java
   data.createResource(CreateResourceRequest.newBuilder().setSubject("user:anne").setParent("organizations/acme")
       .setResourceId("ledger").setResource(Resource.newBuilder().setType("acme.dev/Project")).build()).await();
   ```

3. Grant Bob the relation `member` on the organization:

   ```java
   AccessGrpcClient access = AccessGrpcClient.create(client);
   access.createGrant(CreateGrantRequest.newBuilder().setSubject("user:anne").setGrant(Grant.newBuilder()
       .setObject("organizations/acme").setRelation("member").setGrantee("user:bob")).build()).await();
   ```

4. Read the project as Bob:

   ```java
   Resource ledger = data.getResource(GetResourceRequest.newBuilder().setSubject("user:bob")
       .setName("projects/ledger").build()).await();
   System.out.println(ledger.getName());
   ```

   ```txt
   projects/ledger
   ```

   Bob views the project because he is a member of its organization. The same call as `user:carol` fails with
   `PERMISSION_DENIED`:

   ```txt
   The subject user:carol has no viewer on projects/ledger.
   ```

## Next steps

- [Subjects and permissions](concepts/subjects-and-permissions.md) explains how Pyrite checks each call.
- [Declare a type](how-to/declare-a-type.md) adds your own fields and relations.
- [Sign in with a password](how-to/sign-in-with-a-password.md) adds accounts and sessions.
