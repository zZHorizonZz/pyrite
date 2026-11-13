# About subjects and permissions

Most calls of Pyrite run as a subject, and Pyrite checks the permission of the subject before it reads or changes
anything. A permission is a relation of a declared type, which OpenFGA evaluates.

```java
boolean allowed = access.check(CheckRequest.newBuilder().setSubject("user:bob").setObject("projects/ledger")
    .setPermission("can_edit").build()).await().getAllowed();
```

Bob asks whether he has `can_edit` on `projects/ledger`. Pyrite finds the project, asks OpenFGA about the object
`project:ledger`, and gives `allowed: true` or `allowed: false`.

## The subject

The subject is a principal of the form `type:id`, such as `user:anne`, which each request names in its `subject`
field. The type matches `[a-z][a-z0-9_]{0,49}`, and the id has no `#`, `*`, white space or control characters.
Pyrite does not verify the subject. The caller sets it from a credential that it verified, such as an access token
that `Authenticate` accepted.

Serve Pyrite only to code that you control: a gateway, or a verticle in the same Vert.x instance. A client that sets
`subject` itself acts as anyone.

A few calls take no subject: `WriteDeclaration` and `ReadDeclaration`, `Purge`, which your application makes on its own
behalf, and `ContinueExchange` and `Authenticate`, which prove who the caller is. `StartExchange` takes one only for an
exchange whose input is the account that calls.

The type `user` is an account, so `user:anne` is the principal of `accounts/anne`. A grant names its grantee in one of
three forms:

| Grantee                | Means                                                 |
| ---------------------- | ----------------------------------------------------- |
| `user:anne`            | One principal                                         |
| `user:*`               | Every principal of the type                           |
| `group:finance#member` | Every principal that has `member` on `groups/finance` |

The relation decides which forms and types it takes, in its `metadata`. A grant that makes a cycle of sets, such as
the members of one group as members of another that is already in it, fails with `FAILED_PRECONDITION`.

## Checks

A check asks whether a principal has a relation on a known resource. Pyrite asks OpenFGA, with the object of the
resource, such as `project:ledger`. OpenFGA follows the whole rewrite of the relation: direct grants, computed
relations, tuple to usersets, unions, intersections and differences.

A relation that reads from the parent needs nothing more. Pyrite wrote the `parent` tuple when the resource became
known, so OpenFGA walks up to the organization itself. A block on the organization reaches the project the same way,
when the project's relation subtracts it.

With `principal` empty, the subject checks itself, and needs no permission. To check another principal, the subject
needs the type's `grant` permission on the resource. A permission that the type does not declare fails with
`INVALID_ARGUMENT`. A denied check is no error: `Check` gives `allowed: false`.

## Conditions

A grant can carry a condition that the declaration declares, such as `not_expired`, with values for some of its
parameters. The caller gives the values of the rest, such as `current_time`, in the `context` of its request. A value
that the grant holds wins over a value of the same name in the context:

```java
access.check(CheckRequest.newBuilder().setSubject("user:anne").setPrincipal("user:bob")
    .setObject("projects/ledger").setPermission("can_edit").setContext(Struct.newBuilder()
        .putFields("current_time", Value.newBuilder().setStringValue("2026-05-01T00:00:00Z").build()))
    .build()).await();
```

Each request of the access and cryptography services that checks a permission has a `context`. The authentication
service checks its relations without one. OpenFGA evaluates the conditions as CEL expressions, and fails the call when
a condition on the path of the check misses a value or gets a value of another type.

## Lists

`ListObjects` gives the names of the resources of a type that the subject has a permission on. Pyrite asks OpenFGA's
ListObjects, which follows the same rewrites as a check, `parent` included. The response holds every name, sorted, in
one message. Use it to narrow a read of your own table, such as the projects that a person may view, and filter,
order and page the rows in your application.

`ListSubjects` gives the principals of a type, such as `user`, or of a set, such as `group#member`, that have a
permission on a resource, sorted. A grant to every user gives `user:*`.

`ListGrants` gives a page of the grants on a resource, with an optional relation and grantee. A page holds at most
the smaller of the page size and `tuplesPerRead` grants. `ListChanges` gives a page of the grants and revocations on
the resources of a type, oldest first, from an optional `start_time`. A page of changes leaves out the changes that
the subject may not see, so it can hold fewer changes than its size, and still have a next page.

## Who can ask about whom

| Call                                         | Without the type's `grant` permission             | With it                                |
| -------------------------------------------- | ------------------------------------------------- | -------------------------------------- |
| `Check`                                      | The subject checks itself, with `principal` empty | The subject checks any principal       |
| `ListGrants`                                 | Fails with `PERMISSION_DENIED`                    | Lists the grants on the resource       |
| `ListSubjects`                               | Fails with `PERMISSION_DENIED`                    | Lists the principals with a permission |
| `ListChanges`                                | Leaves out the changes on that resource           | Includes the changes on that resource  |
| `CreateGrant`, `DeleteGrant`, `DeleteObject` | Fails with `PERMISSION_DENIED`                    | Grants, revokes, forgets the resource  |

`ListObjects` needs no permission: the subject lists its own resources.

## What a permission is not

- A separate object. It is a relation of the OpenFGA definition of the type, such as `can_view`.
- Cached. Each call asks OpenFGA again, so a grant counts at the next call.
- Granted by Pyrite on its own. Pyrite grants only the `creator` relation of a new resource, and `owner` of each
  account on itself.

## Related

- [About resources](about-resources.md)
- [Declare a type](../declarations/declare-a-type.md)
- [Troubleshooting access](troubleshooting-access.md)
