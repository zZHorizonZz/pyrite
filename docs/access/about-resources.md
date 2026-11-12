# About resources

A resource is one thing of a declared type, such as the project `projects/ledger`. Your application keeps its
resources in its own tables. Pyrite keeps only what it needs to answer who can do what: that the resource exists,
where it sits, and the grants on it. A resource that Pyrite keeps this way is known.

```java
access.createObject(CreateObjectRequest.newBuilder().setSubject("user:anne").setName("projects/ledger")
    .setParent("organizations/acme").build()).await();
```

After this call, Pyrite knows `projects/ledger` under `organizations/acme`. Every grant on the organization that the
project's relations read through `parent` reaches it, and Anne holds the `creator` relation of the type on it.

## Names

The name of a resource is the plural of its type and its id: `projects/ledger`. The name does not hold the parent, and
no two resources have the same name. Use the same name in your tables and in every call to Pyrite.

An id has 1 to 63 characters: lowercase letters, digits and hyphens. It starts with a letter, and does not end with a
hyphen. It matches `[a-z]([a-z0-9-]{0,61}[a-z0-9])?`.

In OpenFGA, the resource is the object of the singular name of its type and its id, such as `project:ledger`. A
grantee names a resource this way, such as `role:stock-admin`. Pyrite names the resources of its own types itself,
such as `keys/payments` and `accounts/anne`. See [Pyrite's types](../declarations/pyrite-types.md).

## Make a resource known

Call `CreateObject` when your application creates a resource. It does these steps, and fails at the first that does
not hold:

1. It finds the type by the first segment of the name, and checks the id.
2. It checks the parent. A type without parents takes no parent. A type with parents needs a known parent of one of
   its parent types.
3. It checks that the subject has the type's `create` permission on the parent. A resource at the top needs no
   permission.
4. It checks that the name is not known already, and records the resource in the table `pyrite_node`, with its type
   and its parent.
5. It writes the tuple `organization:acme parent project:ledger` to OpenFGA, and grants the subject the type's
   `creator` relation, such as `owner`.

A name of no declared type, an id that does not match, and a parent of the wrong type fail with `INVALID_ARGUMENT`. A
parent that Pyrite does not know fails with `NOT_FOUND`, and a name that it knows already with `ALREADY_EXISTS`.
`CreateKey` and `CreateAccount` make their keys and accounts known through the same steps.

Create the row in your table and call `CreateObject` as one piece of work. When `CreateObject` fails, do not keep the
row.

## Use a known resource

`Check`, `CreateGrant`, `DeleteGrant`, `ListGrants` and `ListSubjects` work on known resources, and fail with
`NOT_FOUND` for a resource that Pyrite does not know. `ListObjects` gives the names of the known resources of a type
that the subject has a permission on, including those that it reaches through their parents:

```java
List<String> names = access.listObjects(ListObjectsRequest.newBuilder().setSubject("user:bob")
    .setType("acme.dev/Project").setPermission("can_view").build()).await().getNamesList();
```

Read the rows of those names from your table, and apply your own filters and order there.

## Forget a resource

Call `DeleteObject` once the resource is gone for good. The subject needs the type's `grant` permission on it,
because forgetting it revokes every grant on it:

```java
access.deleteObject(DeleteObjectRequest.newBuilder().setSubject("user:anne").setName("projects/ledger").build())
    .await();
```

`DeleteObject` deletes the tuples of the resource in OpenFGA: its `parent` tuple, every grant on it, and every grant
to it or to one of its sets on a resource of any declared type, such as the grant of `binding:stock` on an
organization. Then it removes the resource from `pyrite_node`. A new resource can take the name, without the grants of
the old one.

A resource with known resources under it fails with `FAILED_PRECONDITION`, keys included. Forget them first, from the
lowest level up. Forgetting a key deletes it with its versions, and forgetting an account deletes it as
`DeleteAccount` does. See [Purge deleted resources](purge-deleted-resources.md).

## What a known resource is not

- A copy of your row. Pyrite keeps its name, its type, its parent and the time that it became known.
- A resource that moves. Its parent is set when it becomes known.
- Forgotten when your row is deleted. Until your application calls `DeleteObject`, the resource and its grants stay.

## Related

- [About subjects and permissions](about-subjects-and-permissions.md)
- [About declarations](../declarations/about-declarations.md)
- [Troubleshooting access](troubleshooting-access.md)
