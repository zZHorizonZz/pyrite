# Purge deleted resources

Remove from Pyrite what is gone for good: the resources that your application deleted, with their grants, and
Pyrite's own ended sessions, unfinished enrolments and expired exchanges.

## Before you begin

- Make your resources known with `CreateObject`. See [About resources](about-resources.md).
- Decide when a deleted resource is gone for good: when your application deletes it, if it cannot restore it, or when
  its retention in your own table ends. Until you forget it, its grants stay.
- For `purge()`, declare `pyrite.dev/Account`.

## Forget a resource that your application deleted

Pyrite does not forget a resource on its own. Forget it as a subject with the type's `grant` permission on it.

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

`DeleteObject` deletes the `parent` tuple of the resource, every grant on it, and every grant to it. A key or an
account under the resource counts as a resource under it, and forgetting one deletes it.

A forgotten resource cannot be restored with its grants. A new resource can take its name, without the grants of the
old one.

## Purge sessions, enrolments and exchanges on a schedule

Call `purge()` of the `Pyrite` that you made, from a periodic timer, such as once an hour:

```java
vertx.setPeriodic(Duration.ofHours(1).toMillis(), id -> pyrite.purge()
    .onSuccess(rows -> System.out.println("Purged " + rows + " rows")));
```

`purge()` is a method of your application, not a call of the services, so no client can make it. It deletes these
rows, of every account, by the clock of the database:

- The sessions that `EndSession` or an `END_SESSIONS` completion ended, and those that passed their `expire_time`. A
  session that went idle stays until its `expire_time`.
- The factors that a newer factor of the same kind replaced.
- The factors whose enrolment no exchange finished within `exchangeLifetime` of its start.
- The exchanges that passed their `expire_time`, with the encrypted response that a retry would get.

## Verify

- After `DeleteObject`, `Check` on the name fails with `NOT_FOUND`, and a new `CreateObject` of the name succeeds.
- The future of `purge()` gives the number of rows that it deleted. `GetSession` of a purged session fails with
  `NOT_FOUND`.

## Related

- [About resources](about-resources.md)
- [About accounts and exchanges](../accounts/about-accounts-and-exchanges.md)
- [Troubleshooting access](troubleshooting-access.md)
