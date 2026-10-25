# Resources

A resource is one thing of a declared type, such as the project `projects/ledger`. Your application keeps its
resources in its own tables. Pyrite keeps only what it needs to answer who can do what: that the resource exists,
where it sits, and the grants on it. A resource that Pyrite keeps this way is known.

## Names

A resource's name is the plural of its type and its id: `projects/ledger`. The name does not hold the parent. Names
are unique across the whole type. Use the same name in your tables and in every call to Pyrite.

An id has 1 to 63 characters: lowercase letters, digits and hyphens. It starts with a letter, and does not end with a
hyphen.

Pyrite names the resources of its own types itself, such as `keys/payments` and `accounts/anne`. For those types,
refer to [Pyrite's types](../reference/pyrite-types.md).

## Make a resource known

Call `CreateObject` when your application creates a resource:

```java
access.createObject(CreateObjectRequest.newBuilder().setSubject("user:anne").setName("projects/ledger")
    .setParent("organizations/acme").build()).await();
```

`CreateObject` does three things:

1. It checks that the subject has the type's `create` permission on the parent. A type without parents takes no
   parent, and needs no permission.
2. It records the resource in the table `pyrite_node`, with its type and its parent.
3. It writes the tuple `organization:acme parent project:ledger` to OpenFGA, and grants the subject the type's
   `creator` relation, such as `owner`.

A name that Pyrite knows already fails with `ALREADY_EXISTS`. A parent that Pyrite does not know fails with
`NOT_FOUND`.

Create the row in your table and call `CreateObject` as one piece of work. When `CreateObject` fails, do not keep the
row.

## Use a known resource

`Check`, `CreateGrant`, `DeleteGrant`, `ListGrants` and `ListSubjects` work on known resources. A resource that
Pyrite does not know fails with `NOT_FOUND`.

`ListObjects` returns the names of the resources of a type that the subject has a permission on, including those that
the subject reaches through their parents:

```java
List<String> names = access.listObjects(ListObjectsRequest.newBuilder().setSubject("user:bob")
    .setType("acme.dev/Project").setPermission("can_view").build()).await().getNamesList();
```

Read the rows of those names from your table, and apply your own filters and order there.

## Forget a resource

Call `DeleteObject` once the resource is gone, as the subject that deletes it:

```java
access.deleteObject(DeleteObjectRequest.newBuilder().setSubject("user:anne").setName("projects/ledger").build())
    .await();
```

`DeleteObject` removes the resource from `pyrite_node`, with its `parent` tuple, every grant on it, and every grant to
it, such as the grant of `binding:stock` on an organization. A new resource can then take the name, with no grants
from the old one.

A resource with known resources under it fails with `FAILED_PRECONDITION`. Forget them first, from the lowest level
up. The subject needs the type's grant permission on the resource, since forgetting it revokes every grant on it. For
the steps, refer to [Purge deleted resources](../how-to/purge-deleted-resources.md).

## What a known resource is not

- A known resource is not a copy of your row. Pyrite keeps its name, its type, its parent and when it became known.
- A known resource does not move. Its parent is set when it becomes known.
- A deleted row is not forgotten. Until your application calls `DeleteObject`, the resource and its grants stay.
