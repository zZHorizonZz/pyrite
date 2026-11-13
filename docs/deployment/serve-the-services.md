# Serve the services

Serve Pyrite's four gRPC services, schema, access, cryptography and authentication, to the callers of your
application, over the Vert.x event bus or over the network.

## Before you begin

- Create the tables. See [Create the tables](create-the-tables.md).
- Create a Vert.x pool of your database, and the Cinder driver that matches it, such as `new PostgresDriver()`.
- Have a client of OpenFGA's gRPC API, `OpenFGAServiceGrpcClient`: Spectrum's service on the same database, or an
  OpenFGA server. See [Keep the grants in an OpenFGA server](#keep-the-grants-in-an-openfga-server).

`Pyrite.pyrite` makes the four services on one database, and `bind` adds them to a server: an
`EventBusGrpcServer` serves callers in the same Vert.x instance or cluster, and a `GrpcServer` serves callers over
the network.

## Serve the services over the event bus

1. Add `io.vertx:vertx-grpc-eventbus` to your dependencies.

2. Create the options. See [Options](options.md):

   ```java
   PyriteOptions options = new PyriteOptions();
   ```

   Keys need a root key in the options, and accounts need the options of authentication. See
   [Give Pyrite a root key](#give-pyrite-a-root-key) and
   [Sign in with a password](../accounts/sign-in-with-a-password.md).

3. Start Pyrite, and add its services to an event bus server:

   ```java
   EventBusGrpcServer server = EventBusGrpcServer.server(vertx).await();
   Pyrite pyrite = Pyrite.pyrite(vertx, pool, driver, openFga, options).bind(server);
   ```

   Pyrite copies the options, so a change to `options` after this line does not reach it.

4. Call the services from any verticle of the Vert.x instance, or of the cluster:

   ```java
   EventBusGrpcClient client = EventBusGrpcClient.client(vertx).await();
   AccessGrpcClient access = AccessGrpcClient.create(client);
   ```

## Serve the services over gRPC

Pyrite trusts the `subject` of each request. Serve it over the network only to callers that you control, such as your
gateway, on a network that others cannot reach.

1. Add the services to a gRPC server, and listen on a port:

   ```java
   GrpcServer grpc = GrpcServer.server(vertx, new GrpcServerOptions().setMaxMessageSize(16 << 20));
   Pyrite.pyrite(vertx, pool, driver, openFga, options).bind(grpc);
   vertx.createHttpServer().requestHandler(grpc).listen(9090).await();
   ```

   gRPC on Vert.x takes messages of up to 256 KiB unless its options say more. Raise the limit on the server and on
   the client when callers encrypt or sign larger data.

2. Call the services with a gRPC client:

   ```java
   AccessGrpcClient access = AccessGrpcClient.create(GrpcClient.client(vertx),
       SocketAddress.inetSocketAddress(9090, "pyrite.internal"));
   ```

To serve only some services, add them one by one in place of `bind`: `pyrite.access()`, `pyrite.authentication()`,
`pyrite.cryptography()` and `pyrite.schema()` each give one, for `server.addService(...)`.

## Keep the grants in an OpenFGA server

Pyrite needs only a client of OpenFGA's gRPC API. With Spectrum, add its service to the same server, on the same pool:

```java
server.addService(OpenFgaService.create(vertx, pool, driver));
OpenFGAServiceGrpcClient openFga = OpenFGAServiceGrpcClient.create(EventBusGrpcClient.client(vertx).await());
```

To use an OpenFGA server instead, create the client on its gRPC address:

```java
OpenFGAServiceGrpcClient openFga = OpenFGAServiceGrpcClient.create(GrpcClient.client(vertx),
    SocketAddress.inetSocketAddress(8081, "openfga.internal"));
```

Pyrite creates the store `pyrite` at its first declaration, and writes the authorization model of each declaration
there.

## Give Pyrite a root key

A declaration with `pyrite.dev/Key` needs a root key, which seals the material of every key version. The root key is
a Tink `Aead`. Keep it in a KMS, such as Google Cloud KMS with `com.google.crypto.tink:tink-gcpkms`:

```java
Aead root = new GcpKmsClient().withDefaultCredentials()
    .getAead("gcp-kms://projects/acme/locations/global/keyRings/pyrite/cryptoKeys/root");
PyriteOptions options = new PyriteOptions().setKeyOptions(new KeyOptions().setRoot(root));
```

Give every Pyrite on the database the same root key. A version that one root key sealed does not open with another,
and a root key that is lost loses every key.

## Verify

Write a declaration with `WriteDeclaration`. It returns the id of the declaration, and `ReadDeclaration` with an empty
`id` returns it. Before the first declaration, every call of the access, cryptography and authentication services
fails with `FAILED_PRECONDITION`. See [Troubleshooting deployment](troubleshooting-deployment.md).

## Related

- [Options](options.md)
- [Intercept exchanges](../accounts/intercept-exchanges.md)
- [About subjects and permissions](../access/about-subjects-and-permissions.md)
- [Vert.x gRPC](https://vertx.io/docs/vertx-grpc/java/)
