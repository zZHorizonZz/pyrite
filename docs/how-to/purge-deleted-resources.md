# Purge deleted resources

Purge the resources whose retention has ended, so that their rows and grants leave the database. Pyrite never purges
on its own. Call `PurgeResources` on a schedule, such as once an hour.

## Before you begin

- Set the `retention` of each type in the declaration. A deleted resource stays until its retention ends.

## Purge on a schedule

1. Create a client of the data service:

   ```java
   DataGrpcClient data = DataGrpcClient.create(EventBusGrpcClient.client(vertx).await());
   ```

2. Call `PurgeResources` from a periodic timer:

   ```java
   vertx.setPeriodic(Duration.ofHours(1).toMillis(), id -> data.purgeResources(
       PurgeResourcesRequest.getDefaultInstance())
       .onSuccess(response -> System.out.println("Purged " + response.getPurged() + " resources")));
   ```

`PurgeResources` takes no subject. It removes every resource whose `purge_time` has passed, of every type, with the
grants on it. Pyrite's own resources beneath it go too, such as the versions of a key or the sessions of an account.

WARNING: A purged resource cannot be undeleted. Its grants are gone from OpenFGA, and a new resource can take its
name.

## Check the purge

`response.getPurged()` is the number of resources that the call removed. `GetResource` on a purged name fails with
`NOT_FOUND`.

## Related resources

- [Resources](../concepts/resources.md)
- [Declaration](../reference/declaration.md)
