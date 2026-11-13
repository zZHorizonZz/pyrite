# Access

The access service, `pyrite.access.v1.Access`, answers who can do what on the resources of your application. Your
application keeps its resources and tells Pyrite when one is created and when one is gone. Pyrite keeps where each
sits, writes the grants on it to OpenFGA, and answers checks and lists. Every call runs as its subject.

## Features

**Known resources.** Make a resource known when your application creates it, with its parent, so that grants on the
parent reach it and its creator holds a relation on it. See [About resources](about-resources.md).

**Grants.** Grant and revoke the relations of a type, to a user, to every user, or to the members of a set, with a
condition if the relation takes one. See [About subjects and permissions](about-subjects-and-permissions.md).

**Checks.** Ask whether a subject, or another principal, has a permission on a resource before your application acts
on its own rows. See [About subjects and permissions](about-subjects-and-permissions.md#checks).

**Lists.** List the resources of a type that a subject has a permission on, the principals with a permission on a
resource, the grants on a resource, and the changes of grants. See
[About subjects and permissions](about-subjects-and-permissions.md#lists).

**Forgetting.** Forget a resource once your application deleted it for good, with every grant on it and to it, and
purge Pyrite's own ended sessions and unfinished enrolments. See [Purge deleted resources](purge-deleted-resources.md).

A call that names a resource that Pyrite does not know fails with `NOT_FOUND`, and a call whose subject lacks the
relation that it checks fails with `PERMISSION_DENIED`. The errors of access are in
[Troubleshooting access](troubleshooting-access.md).

## Related

- [Declarations](../declarations/README.md)
- [Accounts](../accounts/README.md)
- [OpenFGA: Concepts](https://openfga.dev/docs/concepts)
