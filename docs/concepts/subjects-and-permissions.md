# Subjects and permissions

Most calls of Pyrite run as a subject. Pyrite checks the subject's permission before it reads or changes anything.
This page explains who the subject is, and how Pyrite answers a check and a list.

## The subject

The subject is a principal of the form `type:id`, such as `user:anne`. Each request names it in its `subject` field.
Pyrite does not verify it. The caller sets it from a credential that the caller verified, such as a session token
that `Authenticate` accepted.

Serve Pyrite only to code that you control: a gateway, or a verticle in the same Vert.x instance. A client that sets
`subject` itself acts as anyone.

A few calls take no subject: `Purge`, which your application makes on its own behalf, and
`ContinueFlow` and `Authenticate`, which prove who the caller is. `StartFlow` takes one only for a flow whose input
includes the account that calls.

The type `user` is an account. `user:anne` is the principal of the account `accounts/anne`. Grants can also name
other principals:

| Grantee                | Means                                           |
| ---------------------- | ----------------------------------------------- |
| `user:anne`            | One user                                        |
| `user:*`               | Every user                                      |
| `group:finance#member` | Every principal that has `member` on that group |

## Checks

A check asks whether a principal has a relation on a known resource. Pyrite asks OpenFGA, with the resource's object,
such as `project:ledger`. OpenFGA follows the whole rewrite of the relation: direct grants, computed relations, tuple
to usersets, unions, intersections and exclusions.

A relation that reads from the parent needs nothing more. Pyrite wrote the `parent` tuple when the resource became
known, so OpenFGA walks up to the organization itself. For example, `can_manage_stock` on a project can come from the
project's own role bindings or from its organization's.

Your application checks a permission with `Check` before it acts on its own rows:

```java
boolean allowed = access.check(CheckRequest.newBuilder().setSubject("user:bob").setObject("projects/ledger")
    .setPermission("can_edit").build()).await().getAllowed();
```

With `principal` empty, the subject checks itself.

## Conditions

A grant can carry a condition that the declaration names, such as `not_expired`, with values for some of its
parameters. The caller gives the values of the rest, such as `current_time`, in the `context` of its request. The
requests of the access and cryptography services that check a permission have a `context`. Without the values that a
condition on the path needs, the call fails with `INVALID_ARGUMENT`.

```java
access.check(CheckRequest.newBuilder().setSubject("user:anne").setPrincipal("user:bob")
    .setObject("projects/ledger").setPermission("can_edit").setContext(Struct.newBuilder()
        .putFields("current_time", Value.newBuilder().setStringValue("2026-05-01T00:00:00Z").build()))
    .build()).await();
```

Spectrum and OpenFGA evaluate conditions as CEL expressions.

## Lists

`ListObjects` returns the names of the resources of a type that the subject has a permission on. Pyrite asks
OpenFGA's ListObjects, which follows the same rewrites as a check, `parent` included. The response holds every name,
sorted, in one message.

Use it to narrow a read of your own table, such as the projects that a person may view. Your application filters,
orders and pages the rows itself.

## Who can ask about whom

| Call           | Without a grant permission                        | With the type's `grant` permission     |
| -------------- | ------------------------------------------------- | -------------------------------------- |
| `Check`        | The subject checks itself, with `principal` empty | The subject checks any principal       |
| `ListGrants`   | Fails with `PERMISSION_DENIED`                    | Lists the grants on the resource       |
| `ListSubjects` | Fails with `PERMISSION_DENIED`                    | Lists the principals with a permission |
| `ListChanges`  | Leaves out the changes on that resource           | Includes the changes on that resource  |

## What a permission is not

- A permission is not a separate object. It is a relation of the type's OpenFGA definition, such as `can_view`.
- A permission is not cached. Each call asks OpenFGA again.
- A denied check is not an error. `Check` returns `allowed: false`. It fails with `NOT_FOUND` only when Pyrite does
  not know the resource.
