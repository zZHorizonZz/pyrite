# Subjects and permissions

Every call of the data and access services runs as a subject. Pyrite checks the subject's permission before it reads
or changes anything. This page explains who the subject is, and how Pyrite answers a check and a query.

## The subject

The subject is a principal of the form `type:id`, such as `user:anne`. Each request names it in its `subject` field.
Pyrite does not verify it. The caller sets it from a credential that the caller verified, such as a session token
that `Authenticate` accepted.

Serve Pyrite only to code that you control: a gateway, or a verticle in the same Vert.x instance. A client that sets
`subject` itself acts as anyone.

The type `user` is an account. `user:anne` is the principal of the account `accounts/anne`. Grants can also name
other principals:

| Grantee                | Means                                           |
| ---------------------- | ----------------------------------------------- |
| `user:anne`            | One user                                        |
| `user:*`               | Every user                                      |
| `group:finance#member` | Every principal that has `member` on that group |

## Checks

A check asks whether a principal has a relation on a resource. Most relations are OpenFGA's to answer: Pyrite asks
OpenFGA, with the resource's object, such as `project:ledger`.

A relation that reads from the parent is different, because Pyrite writes no `parent` tuples. Pyrite follows the
relation's rewrite itself:

1. **Direct grants, computed relations and other tuplesets:** OpenFGA answers them on the resource.
2. **A tuple to userset through `parent`:** Pyrite reads the resource's parent, and checks the relation there.
3. **Unions, intersections and exclusions:** Pyrite combines the answers of their branches. A union stops at the
   first branch that allows.

When a relation mixes both kinds, Pyrite gives OpenFGA the parts that do not read from the parent as hidden
relations, named `<relation>.<n>`. For example, `can_manage_stock` on a project can come from the project's own role
bindings or from its organization. OpenFGA answers the bindings on each resource that Pyrite visits, and Pyrite walks
up to the organization.

A walk stops after `maxDepth` ancestors, 64 by default. For the option, refer to [Options](../reference/options.md).

## Conditions

A grant can carry a condition that the declaration names, such as `not_expired`, with values for some of its
parameters. The caller gives the values of the rest, such as `current_time`, in the `context` of its request. Every
request that checks a permission has a `context`: the calls of the data, access and cryptography services. Without
the values that a condition on the path needs, the call fails with `INVALID_ARGUMENT`.

```java
access.check(CheckRequest.newBuilder().setSubject("user:anne").setPrincipal("user:bob")
    .setObject("projects/ledger").setPermission("can_edit").setContext(Struct.newBuilder()
        .putFields("current_time", Value.newBuilder().setStringValue("2026-05-01T00:00:00Z").build()))
    .build()).await();
```

Spectrum and OpenFGA evaluate conditions as CEL expressions.

## Queries

`QueryResources` returns the resources of a type that the subject has a permission on. Pyrite does not check every
row. It finds where the permission can come from, and reads only the rows beneath:

1. Pyrite lists the objects on which the subject holds each relation that leads to the permission, with OpenFGA's
   ListObjects. These are the anchors, such as the organizations where the subject is a member.
2. It reads the resources beneath the anchors, level by level, down to the queried type.
3. It reads a page of the queried type's table: rows that are anchors themselves, or whose parent is one. The
   request's `filter` and `order_by` apply to the same read.
4. It checks the permission on each row of the page, and leaves out the rows where the check fails.

Because of the last step, a page can hold fewer resources than the page size, even none, before the last page.
Read pages until `next_page_token` is empty.

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
- A denied read is not hidden. `GetResource` fails with `PERMISSION_DENIED` when the resource exists, and with
  `NOT_FOUND` when it does not.
