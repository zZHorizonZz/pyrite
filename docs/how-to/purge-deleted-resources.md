# Purge deleted resources

Remove what is gone for good, in two places. When your application purges one of its own resources, it tells Pyrite
to forget the resource and its grants. Pyrite's own records, ended sessions and unfinished enrolments, leave with
`Purge`. Pyrite does neither on its own.

## Before you begin

- Make your resources known with `CreateObject`. For more information, refer to
  [Resources](../concepts/resources.md).
- For `Purge`, declare `pyrite.dev/Account` and add the authentication service to your server.

## Forget a resource that your application purges

Call `DeleteObject` once the resource is gone, as the subject that deletes it, which needs the type's grant permission
on it. Call it when the resource is first deleted if your application cannot restore it, or once its retention in your
own table ends if it can: until then, its grants stay.

1. Forget the resources under it first, from the lowest level up:

   ```java
   access.deleteObject(DeleteObjectRequest.newBuilder().setSubject("user:anne").setName("projects/ledger").build())
       .await();
   ```

2. Forget the resource:

   ```java
   access.deleteObject(DeleteObjectRequest.newBuilder().setSubject("user:anne").setName("organizations/acme")
       .build()).await();
   ```

3. Delete the rows from your tables.

`DeleteObject` removes the resource's `parent` tuple, every grant on it, and every grant to it.

WARNING: A forgotten resource cannot be restored with its grants. A new resource can take its name, with no grants
from the old one.

## Purge sessions and enrolments on a schedule

1. Create a client of the authentication service:

   ```java
   AuthenticationGrpcClient authentication = AuthenticationGrpcClient.create(
       EventBusGrpcClient.client(vertx).await());
   ```

2. Call `Purge` from a periodic timer, such as once an hour:

   ```java
   vertx.setPeriodic(Duration.ofHours(1).toMillis(), id -> authentication.purge(PurgeRequest.getDefaultInstance())
       .onSuccess(response -> System.out.println("Purged " + response.getPurged() + " rows")));
   ```

`Purge` takes no subject. It deletes these rows, of every account:

- The sessions that passed their `expire_time`, or that `EndSession` or an `END_SESSIONS` completion ended.
- The factors whose enrolment was never finished, and those that a newer factor of the same kind replaced.

## Check the result

- `DeleteObject` succeeded when `Check` on the name fails with `NOT_FOUND`.
- `response.getPurged()` is the number of rows that `Purge` deleted. `GetSession` on a purged session fails with
  `NOT_FOUND`.

## Related resources

- [Resources](../concepts/resources.md)
- [Accounts and flows](../concepts/accounts-and-flows.md)
