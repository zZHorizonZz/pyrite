# Get started

In this guide, you serve Pyrite on PostgreSQL over the Vert.x event bus. You declare organizations and their
projects, make one of each known to Pyrite, grant a relation on the organization, and check what another user may do
on the project.

## Before you begin

- Java 25
- A PostgreSQL 13 or later database
- Flyway, to create the tables

## 1. Add the dependencies

Add Pyrite, the Cinder driver of your database, its Vert.x client, and the Vert.x gRPC event bus:

<!-- x-release-please-start-version -->
```xml
<dependency>
  <groupId>dev.horizon</groupId>
  <artifactId>pyrite</artifactId>
  <version>0.2.1-SNAPSHOT</version>
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

Pyrite brings Spectrum, which serves OpenFGA's API from the same database. Pyrite keeps its grants there. Pyrite
also contains its own copy of CEL, which evaluates the identifier kinds of a declaration, so you add no CEL
dependency.

## 2. Create the tables

Run Pyrite's migrations and Spectrum's, each with its own history table:

```java
String url = "jdbc:postgresql://localhost:5432/app";
for (String[] migrations : new String[][] { { "pyrite/migration/**/postgresql", "pyrite" },
    { "spectrum/migration/**/postgresql", "spectrum" } }) {
  Flyway.configure().dataSource(url, "app", "secret").locations("classpath:" + migrations[0])
      .table(migrations[1] + "_schema_history").baselineOnMigrate(true).load().migrate();
}
```

Pyrite does not store your resources. Your application keeps them in its own tables, as it does today. For more
information, refer to [Create the tables](how-to/create-the-tables.md).

## 3. Declare the types

Save the declaration as `declaration.textproto`. It is a `pyrite.schema.v1.Declaration` in protobuf text format.
A member of an organization views its projects, and the creator of each resource is granted a relation on it:

```textproto
# proto-message: pyrite.schema.v1.Declaration

types {
  name: "acme.dev/Organization"
  plural: "organizations"
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
  permissions { grant: "admin" }
  grantable: ["admin", "member"]
  creator: "admin"
}

types {
  name: "acme.dev/Project"
  plural: "projects"
  parents: "acme.dev/Organization"
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
  permissions { create: "admin" grant: "owner" }
  grantable: ["owner", "viewer"]
  creator: "owner"
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
PostgresDriver driver = new PostgresDriver();
server.addService(OpenFgaService.create(vertx, pool, driver));
OpenFGAServiceGrpcClient openFga = OpenFGAServiceGrpcClient.create(client);

PyriteServiceOptions options = new PyriteServiceOptions();
server.addService(SchemaService.create(vertx, pool, driver, openFga, options));
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

1. Your application creates an organization in its own table. Make it known to Pyrite as `user:anne`. Anne becomes
   its `admin`, the type's `creator` relation:

   ```java
   AccessGrpcClient access = AccessGrpcClient.create(client);
   access.createObject(CreateObjectRequest.newBuilder().setSubject("user:anne").setName("organizations/acme")
       .build()).await();
   ```

2. Make a project in it known. Anne needs `admin` on the organization, the type's `create` permission:

   ```java
   access.createObject(CreateObjectRequest.newBuilder().setSubject("user:anne").setName("projects/ledger")
       .setParent("organizations/acme").build()).await();
   ```

3. Grant Bob the relation `member` on the organization:

   ```java
   access.createGrant(CreateGrantRequest.newBuilder().setSubject("user:anne").setGrant(Grant.newBuilder()
       .setObject("organizations/acme").setRelation("member").setGrantee("user:bob")).build()).await();
   ```

4. Check whether Bob views the project, before your application shows it to him:

   ```java
   boolean allowed = access.check(CheckRequest.newBuilder().setSubject("user:bob").setObject("projects/ledger")
       .setPermission("viewer").build()).await().getAllowed();
   System.out.println(allowed);
   ```

   ```txt
   true
   ```

   Bob views the project because he is a member of its organization. The same check as `user:carol` returns
   `false`.

5. List the projects that Bob views:

   ```java
   List<String> projects = access.listObjects(ListObjectsRequest.newBuilder().setSubject("user:bob")
       .setType("acme.dev/Project").setPermission("viewer").build()).await().getNamesList();
   ```

   ```txt
   [projects/ledger]
   ```

## Next steps

- [Subjects and permissions](concepts/subjects-and-permissions.md) explains how Pyrite checks each call.
- [Declare a type](how-to/declare-a-type.md) adds your own types and relations.
- [Sign in with a password](how-to/sign-in-with-a-password.md) adds accounts and sessions.
