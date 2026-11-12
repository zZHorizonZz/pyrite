# Get started

In this guide, you serve Pyrite on PostgreSQL over the Vert.x event bus. You declare organizations and their
projects, make one of each known to Pyrite, grant a relation on the organization, and check what another user may do
on the project.

## Prerequisites

- Java 25 or later, Maven and Vert.x 5.
- A GitHub account. GitHub Packages requires a token, also for public packages.
- A PostgreSQL 13 or later database.
- Flyway, to create the tables.

## 1. Add the dependencies

1. Add the GitHub Packages repositories of Pyrite and Cinder, and the dependencies of Pyrite, the Cinder driver of
   your database, its Vert.x client and the Vert.x gRPC event bus, to your `pom.xml`:

   <!-- x-release-please-start-version -->
   ```xml
   <repositories>
     <repository>
       <id>github-pyrite</id>
       <url>https://maven.pkg.github.com/zZHorizonZz/pyrite</url>
     </repository>
     <repository>
       <id>github-cinder</id>
       <url>https://maven.pkg.github.com/zZHorizonZz/cinder</url>
     </repository>
   </repositories>

   <dependencies>
     <dependency>
       <groupId>dev.horizon</groupId>
       <artifactId>pyrite</artifactId>
       <version>0.3.2-SNAPSHOT</version>
     </dependency>
   </dependencies>
   ```
   <!-- x-release-please-end -->

   ```xml
   <dependency>
     <groupId>dev.horizon</groupId>
     <artifactId>cinder-database-postgres</artifactId>
     <version>0.6.0</version>
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

2. Create a personal access token with the `read:packages` scope, and add it to `~/.m2/settings.xml` for the ids of
   your repositories and of those that Pyrite's POM names, `github-aquery`, `github-basepoint`, `github-leadseal` and
   `github-spectrum`:

   ```xml
   <settings>
     <servers>
       <server>
         <id>github-pyrite</id>
         <username>YOUR_GITHUB_USERNAME</username>
         <password>YOUR_TOKEN</password>
       </server>
       <!-- The same for github-cinder, github-aquery, github-basepoint, github-leadseal and github-spectrum. -->
     </servers>
   </settings>
   ```

Pyrite brings Spectrum, which serves OpenFGA's API from the same database, and keeps its grants there. The JAR holds
its own copy of CEL, so you add no CEL dependency.

## 2. Create the tables

Run Pyrite's migrations and Spectrum's, each with its own history table. Flyway needs its module
`flyway-database-postgresql` and the PostgreSQL JDBC driver:

```java
String url = "jdbc:postgresql://localhost:5432/app";
for (String set : List.of("pyrite", "spectrum")) {
  Flyway.configure().dataSource(url, "app", "secret").locations("classpath:" + set + "/migration/**/postgresql")
      .table(set + "_schema_history").baselineOnMigrate(true).load().migrate();
}
```

Pyrite does not store your resources. Your application keeps them in its own tables. See
[Create the tables](deployment/create-the-tables.md).

## 3. Declare the types

Save the declaration as `declaration.textproto`. It is a `pyrite.schema.v1.Declaration` in protobuf text format. A
member of an organization views its projects, and the creator of each resource gets a relation on it:

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

Pyrite adds the relation `parent` to each type with parents, so `tuple_to_userset` reaches the organization of a
project. See [Declaration reference](declarations/declaration-reference.md).

## 4. Serve the services

Add Spectrum's service and Pyrite's services, on one pool, to an event bus server:

```java
Vertx vertx = Vertx.vertx();
Pool pool = PgBuilder.pool().connectingTo("postgresql://app:secret@localhost:5432/app").using(vertx).build();
PostgresDriver driver = new PostgresDriver();

EventBusGrpcServer server = EventBusGrpcServer.server(vertx).await();
EventBusGrpcClient client = EventBusGrpcClient.client(vertx).await();
server.addService(OpenFgaService.create(vertx, pool, driver));
OpenFGAServiceGrpcClient openFga = OpenFGAServiceGrpcClient.create(client);

Pyrite.pyrite(vertx, pool, driver, openFga, new PyriteOptions()).bind(server);
```

`bind` adds the four services: schema, access, cryptography and authentication. See
[Serve the services](deployment/serve-the-services.md).

## 5. Write the declaration

Parse the file, and write it with the schema service:

```java
Declaration.Builder declaration = Declaration.newBuilder();
TextFormat.merge(Files.readString(Path.of("declaration.textproto")), declaration);

SchemaGrpcClient schema = SchemaGrpcClient.create(client);
String id = schema.writeDeclaration(WriteDeclarationRequest.newBuilder().setDeclaration(declaration).build())
    .await().getId();
```

Every service reads the latest declaration from then on.

## 6. Call the services

1. Your application creates an organization in its own table. Make it known to Pyrite as `user:anne`. Anne gets
   `admin`, the type's `creator` relation:

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
   ```

5. List the projects that Bob views:

   ```java
   List<String> projects = access.listObjects(ListObjectsRequest.newBuilder().setSubject("user:bob")
       .setType("acme.dev/Project").setPermission("viewer").build()).await().getNamesList();
   ```

## Verify

`allowed` is `true`, because Bob is a member of the organization of the project, and `projects` is
`[projects/ledger]`. The same check as `user:carol` returns `false`. A call before step 5 fails with
`FAILED_PRECONDITION: No declaration is written; write one with pyrite.schema.v1.Schema.WriteDeclaration.` See
[Troubleshooting deployment](deployment/troubleshooting-deployment.md).

## Next steps

- [About subjects and permissions](access/about-subjects-and-permissions.md): how Pyrite checks each call.
- [Declare a type](declarations/declare-a-type.md): your own types and relations.
- [Sign in with a password](accounts/sign-in-with-a-password.md): accounts and sessions.
- [Encrypt data](keys/encrypt-data.md): keys and their versions.
