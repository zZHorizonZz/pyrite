# Serve the services

Each of Pyrite's five services is a Vert.x gRPC service. Add the ones that you use to the server that your callers
reach. An event bus server serves callers in the same Vert.x instance or cluster. A gRPC server serves callers over
the network.

## Before you begin

- Create the tables. For more information, refer to [Create the tables](create-the-tables.md).
- Create a Vert.x pool of your database, and the Cinder driver that matches it, such as `new PostgresDriver()`.
- Have a client of OpenFGA's gRPC API: Spectrum's service on the same database, or an OpenFGA server.

## Serve the services over the event bus

1. Add `io.vertx:vertx-grpc-eventbus` to your dependencies.

2. Create one options object, and give it to every service:

   ```java
   PyriteServiceOptions options = new PyriteServiceOptions();
   ```

   The options hold the key of page tokens. A token that one service gave is good at another only when both have
   the same key.

3. Add the services to an event bus server:

   ```java
   EventBusGrpcServer server = EventBusGrpcServer.server(vertx).await();
   server.addService(SchemaService.create(vertx, pool, driver, openFga, options));
   server.addService(DataService.create(vertx, pool, driver, openFga, options));
   server.addService(AccessService.create(vertx, pool, driver, openFga, options));
   ```

4. Call them from any verticle of the Vert.x instance, or of the cluster:

   ```java
   EventBusGrpcClient client = EventBusGrpcClient.client(vertx).await();
   DataGrpcClient data = DataGrpcClient.create(client);
   ```

## Serve the services over gRPC

Pyrite trusts the `subject` of each request. Serve it over the network only to callers that you control, such as
your gateway, on a network that others cannot reach.

1. Add the services to a gRPC server, and listen on a port:

   ```java
   GrpcServer grpc = GrpcServer.server(vertx);
   grpc.addService(DataService.create(vertx, pool, driver, openFga, options));
   grpc.addService(AccessService.create(vertx, pool, driver, openFga, options));
   vertx.createHttpServer().requestHandler(grpc).listen(9090).await();
   ```

2. Call them with a gRPC client:

   ```java
   DataGrpcClient data = DataGrpcClient.create(GrpcClient.client(vertx),
       SocketAddress.inetSocketAddress(9090, "pyrite.internal"));
   ```

## Keep the grants in an OpenFGA server

Pyrite needs only a client of OpenFGA's gRPC API, `OpenFGAServiceGrpcClient`. Spectrum's service on the same database
is the simplest. To use an OpenFGA server instead, create the client on its gRPC address:

```java
OpenFGAServiceGrpcClient openFga = OpenFGAServiceGrpcClient.create(GrpcClient.client(vertx),
    SocketAddress.inetSocketAddress(8081, "openfga.internal"));
```

Pyrite creates the store `pyrite` at its first declaration, and writes its models there.

## Give Pyrite a root key

A declaration with `pyrite.dev/Key` needs a root key, which encrypts the material of every key version. The root key
is a Tink `Aead`. Keep it in a KMS, such as Google Cloud KMS with `com.google.crypto.tink:tink-gcpkms`:

```java
Aead root = new GcpKmsClient().withDefaultCredentials()
    .getAead("gcp-kms://projects/acme/locations/global/keyRings/pyrite/cryptoKeys/root");
PyriteServiceOptions options = new PyriteServiceOptions().withRoot(root);
```

Give every service the same root key. A version that one root key encrypted cannot be read with another.

## Related resources

- [Options](../reference/options.md)
- [Subjects and permissions](../concepts/subjects-and-permissions.md)
- [Vert.x gRPC](https://vertx.io/docs/vertx-grpc/java/)
