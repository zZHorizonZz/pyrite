# About resources

A resource is one thing of a declared type, such as the project `acme.dev/Project:organizations/acme/projects/ledger`.
Your application keeps its resources in its own tables. Pyrite keeps only what it needs to answer who can do what: that
the resource exists, where it sits, and the grants on it. A resource that Pyrite keeps this way is known.

```java
access.createObject(CreateObjectRequest.newBuilder().setSubject("pyrite.dev/Account:accounts/anne")
    .setName("acme.dev/Project:organizations/acme/projects/ledger")
    .setParent("acme.dev/Organization:organizations/acme").build()).await();
```

After this call, Pyrite knows `acme.dev/Project:organizations/acme/projects/ledger` under
`acme.dev/Organization:organizations/acme`. Every grant on the organization that the project's relations read through
`parent` reaches it, and Anne holds the `creator` relation of the type on it.

## Names

The name of a resource is the name of its type, a colon, and an id:
`acme.dev/Project:organizations/acme/projects/ledger`. The name is also the resource's object in OpenFGA, so a grantee
names a resource the same way, such as `acme.dev/Role:organizations/acme/roles/stock-admin`. No two resources have the
same name. Pyrite gives every name back as you made it known, in lists, grants and changes.

The id is yours to choose, such as the relative resource name of the resource in your API. Pyrite takes what OpenFGA
takes:

- The type is a declared type. Its name has 1 to 254 characters, without `:`, `#`, `@` or white space.
- The id has at least one character, and no `:`, `#`, white space or control characters. It is not `*`, which means
  every object of the type. It can hold `/`, `.`, `-`, `_` and `@`.
- The whole name has at most 256 characters.

Pyrite's own resources are named the same way: the key `keys/payments` is the resource `pyrite.dev/Key:keys/payments`,
and the account `accounts/anne` is `pyrite.dev/Account:accounts/anne`. See
[Pyrite's types](../declarations/pyrite-types.md).

## Make a resource known

Call `CreateObject` when your application creates a resource. It does these steps, and fails at the first that does
not hold:

1. It checks that the name is an object that OpenFGA takes, and finds the type by the part of the name before the
   first `:`.
2. It checks the parent. A type without parents takes no parent. A type with parents needs a known parent of one of
   its parent types.
3. It checks that the subject has the type's `create` permission on the parent. A resource at the top needs no
   permission.
4. It checks that the name is not known already, and records the resource in the table `pyrite_node`, with its type
   and its parent.
5. It writes the tuple `acme.dev/Organization:organizations/acme parent
   acme.dev/Project:organizations/acme/projects/ledger` to OpenFGA, and grants the subject the type's `creator`
   relation, such as `owner`.

A name that OpenFGA does not take, a name of no declared type, and a parent of the wrong type fail with
`INVALID_ARGUMENT`. A parent that Pyrite does not know fails with `NOT_FOUND`, and a name that it knows already with
`ALREADY_EXISTS`. `CreateKey` and `CreateAccount` make their keys and accounts known through the same steps.

Create the row in your table and call `CreateObject` as one piece of work. When `CreateObject` fails, do not keep the
row.

## Use a known resource

`Check`, `CreateGrant`, `DeleteGrant`, `ListGrants` and `ListSubjects` work on known resources, and fail with
`NOT_FOUND` for a resource that Pyrite does not know. `ListObjects` gives the names of the known resources of a type
that the subject has a permission on, including those that it reaches through their parents:

```java
List<String> names = access.listObjects(ListObjectsRequest.newBuilder().setSubject("pyrite.dev/Account:accounts/bob")
    .setType("acme.dev/Project").setPermission("can_view").build()).await().getNamesList();
```

Read the rows of those names from your table, and apply your own filters and order there.

## Forget a resource

Call `DeleteObject` once the resource is gone for good. The subject needs the type's `grant` permission on it,
because forgetting it revokes every grant on it:

```java
access.deleteObject(DeleteObjectRequest.newBuilder().setSubject("pyrite.dev/Account:accounts/anne")
    .setName("acme.dev/Project:organizations/acme/projects/ledger").build())
    .await();
```

`DeleteObject` deletes the tuples of the resource in OpenFGA: its `parent` tuple, every grant on it, and every grant to
it or to one of its sets on a resource of any declared type, such as the grant of `acme.dev/Binding:bindings/stock` on
an organization. Then it removes the resource from `pyrite_node`. A new resource can take the name, without the grants
of the old one.

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
