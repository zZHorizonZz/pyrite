# Resources

A resource is one row of a declared type, such as the project `projects/ledger`. The data service creates, reads and
changes resources. Every resource has a name, standard fields, a revision and a lifecycle.

## Names

A resource's name is the plural of its type and its id: `projects/ledger`. The name does not hold the parent, so a
resource keeps its name when it moves. Names are unique across the whole type.

The caller chooses the id when it creates the resource. An id has 1 to 63 characters: lowercase letters, digits and
hyphens. It starts with a letter, and does not end with a hyphen.

Pyrite names the resources of some of its own types itself, such as `keyVersions/payments-2` and `sessions/<id>`.
For those types, refer to [Pyrite's types](../reference/pyrite-types.md).

## Fields

The `fields` map of a `pyrite.data.v1.Resource` holds the columns of the type's table, by name. Each value is a
`pyrite.data.v1.Value` of the kind that the declaration gives the field.

| Field          | Set by     | Holds                                             |
| -------------- | ---------- | ------------------------------------------------- |
| `name`         | Pyrite     | The name, such as `projects/ledger`               |
| `uid`          | Pyrite     | A UUID that Pyrite generates                      |
| `parent`       | Pyrite     | The parent's name, or an empty string at the top  |
| `display_name` | The caller | A name for people                                 |
| `labels`       | The caller | A map of strings, such as `env: "prod"`           |
| `revision`     | Pyrite     | `1` at creation, one more at each change          |
| `create_time`  | Pyrite     | When the resource was created                     |
| `update_time`  | Pyrite     | When the resource last changed                    |
| `delete_time`  | Pyrite     | When the resource was deleted; unset while active |
| `purge_time`   | Pyrite     | When Pyrite may purge it; unset while active      |

The type's own fields, such as `billing_account`, are the caller's to set. Pyrite's own types have fields that only
Pyrite sets, fields that are set once at creation, and fields that Pyrite never returns.

## Revisions

Every change names the revision that the caller read. When the resource is at another revision, the change fails
with `ABORTED`, and nothing changes. Read the resource again, and retry with its new revision.

```java
data.updateResource(UpdateResourceRequest.newBuilder().setSubject("user:anne")
    .setResource(Resource.newBuilder().setName("projects/ledger").putFields("display_name",
        Value.newBuilder().setStringValue("General ledger").build()))
    .setUpdateMask(FieldMask.newBuilder().addPaths("display_name")).setRevision(1).build()).await();
```

An update sets only the fields that its `update_mask` names.

## Moves

`MoveResource` puts a resource under another parent of an allowed type. The subject needs the `update` permission on
the resource and the `create` permission on the new parent. Grants on the resource stay. What it inherits changes at
once, because Pyrite reads the parent at each check. A resource cannot move beneath itself.

## Deletion and purging

A resource leaves in three steps:

1. **Delete.** `DeleteResource` sets `delete_time`, and `purge_time` to the deletion time plus the type's retention.
   The resource stays in its table, and queries leave it out unless they set `show_deleted`. A resource cannot be
   deleted while resources beneath it are active.
2. **Undelete.** Until `purge_time`, `UndeleteResource` makes the resource active again, with its grants.
3. **Purge.** After `purge_time`, `PurgeResources` removes the row and the resource's grants from OpenFGA. A new
   resource can then take the name, with no grants from the old one.

Pyrite does not purge on its own. Call `PurgeResources` on a schedule. For more information, refer to
[Purge deleted resources](../how-to/purge-deleted-resources.md).

## What a resource is not

- A resource is not a document. Its fields are the columns of its table, with the kinds that the declaration gives.
- A resource's name is not a path. It does not change when the resource moves.
- A deleted resource is not gone. It keeps its row, its name and its grants until it is purged.
