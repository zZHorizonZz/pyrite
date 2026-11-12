# Troubleshooting access

The errors of the access service, the most frequent first. Each code block shows the status of the call and its
message. Pyrite makes each of these errors, except a refusal of OpenFGA, before the call writes anything, so the call
changes nothing in Pyrite's tables or in OpenFGA.

## The subject ... has no ... on ...

```text
PERMISSION_DENIED: The subject user:bob has no owner on projects/ledger.
```

**Cause:** The subject does not hold the relation that the call checks. The message names it: the type's `create`
permission on the parent for `CreateObject`, the type's `grant` permission on the resource for the grant methods,
`DeleteObject`, `ListSubjects` and a `Check` of another principal, or the relation that a key or an account call
checks.

**Fix:** Grant the subject the relation, or a relation that leads to it, such as `admin` on the organization. Check
that the request names the subject that you expect.

**Verify:** `Check` with the subject, the resource and the relation gives `allowed: true`, and the call succeeds.

## The resource ... is not known

```text
NOT_FOUND: The resource projects/ledger is not known.
```

**Cause:** Your application never made the resource known with `CreateObject`, or forgot it with `DeleteObject`.
`Check`, the grant and list methods on a resource, `DeleteObject`, and `CreateObject` under the resource as a parent
need it known.

**Fix:** Call `CreateObject` when your application creates the resource. For resources that existed before, make each
one known once, parents first. See [About resources](about-resources.md).

**Verify:** `Check` on the name gives `allowed` instead of failing.

## The subject ... is not a principal of the form type:id

```text
INVALID_ARGUMENT: The subject 'anne' is not a principal of the form type:id, such as user:anne.
```

**Cause:** The `subject` or the `principal` of the request is an id, a resource name, a wildcard or a set, not a
principal.

**Fix:** Name the principal: `user:anne`, not `anne`, `accounts/anne`, `user:*` or `group:finance#member`.

**Verify:** The call no longer fails with `INVALID_ARGUMENT`.

## The resource ... is known already

```text
ALREADY_EXISTS: The resource projects/ledger is known already.
```

**Cause:** `CreateObject` of a name that Pyrite knows, such as a retry of a call that succeeded, or a name that your
application gave twice.

**Fix:** Treat the resource as known, or forget the old one with `DeleteObject` before the name is taken again.

**Verify:** `Check` on the name gives `allowed` instead of failing.

## The resource ... has resources under it; forget them first

```text
FAILED_PRECONDITION: The resource organizations/acme has resources under it; forget them first.
```

**Cause:** `DeleteObject` of a resource while known resources are under it, keys and accounts included.

**Fix:** Forget the resources under it first, from the lowest level up. See
[Purge deleted resources](purge-deleted-resources.md).

**Verify:** `DeleteObject` succeeds, and `Check` on the name fails with `NOT_FOUND`.

## The type ... has no relation ...

```text
INVALID_ARGUMENT: The type acme.dev/Project has no relation can_fly.
```

**Cause:** `Check`, `ListObjects` or `ListSubjects` names a permission that the type does not declare, such as a typo,
or a relation of another type.

**Fix:** Name a relation of the type, such as `can_view`. See
[Declaration reference](../declarations/declaration-reference.md).

**Verify:** The call gives its answer.

## The type ... does not grant ...

```text
INVALID_ARGUMENT: The type acme.dev/Project does not grant can_view.
```

**Cause:** `CreateGrant` or `DeleteGrant` of a relation that is not in the type's `grantable`, such as a relation that
reads other relations, or `parent`.

**Fix:** Grant a relation that the computed relation reads, such as `viewer` for `can_view`, or add the relation to
`grantable` if it takes grants.

**Verify:** `CreateGrant` gives the grant.

## The grantee ... is not of the form type:id, type:* or type:id#relation

```text
INVALID_ARGUMENT: The grantee 'bob' is not of the form type:id, type:* or type:id#relation.
```

**Cause:** The grantee of a grant is an id, a resource name, empty, or a set of every object, such as
`group:*#member`.

**Fix:** Name a principal, such as `user:bob`, every principal of a type, such as `user:*`, or a set, such as
`group:finance#member`.

**Verify:** `CreateGrant` gives the grant.

## Invalid status: actual:..., expected:OK

```text
INVALID_ARGUMENT: Invalid status: actual:INVALID_ARGUMENT, expected:OK
```

**Cause:** OpenFGA refused the call, and Pyrite ends the call with the status of OpenFGA. The gRPC client of Vert.x
gives the status without the message of OpenFGA. OpenFGA refuses a grant to a type or a set that the relation does
not take, such as `project:payroll` or `group:finance` for `owner`, a condition that the relation does not take, a
value of a condition of another type than its parameter, and a check whose conditions miss a value, such as
`current_time`.

**Fix:** Grant only the types and sets that the `metadata` of the relation lists, with the conditions that it lists.
Give the values of conditions in the types of their parameters, such as a time as `2026-06-01T00:00:00Z`, and give
`current_time` and the other values that grants do not hold in the `context` of each call.

**Verify:** The call gives its result.

## A resource of the type ... is under one of ..., but the request names no parent

```text
INVALID_ARGUMENT: A resource of the type acme.dev/Project is under one of [acme.dev/Organization], but the request names no parent.
```

**Cause:** `CreateObject` or `CreateKey` of a resource of a type with parents, with an empty `parent`.

**Fix:** Name the parent, such as `organizations/acme`.

**Verify:** `CreateObject` succeeds, and a grant on the parent reaches the resource.

## A resource of the type ... cannot be under ...

```text
INVALID_ARGUMENT: A resource of the type acme.dev/Project cannot be under projects/ledger.
```

**Cause:** The parent is known, but of a type that is not one of the type's `parents`.

**Fix:** Name a parent of one of the parent types, or declare the type of the parent as a parent type.

**Verify:** `CreateObject` succeeds.

## The type ... has no parents, but the request names ...

```text
INVALID_ARGUMENT: The type acme.dev/Organization has no parents, but the request names organizations/acme.
```

**Cause:** `CreateObject` of a resource of a type at the top, with a `parent`.

**Fix:** Leave `parent` empty, or declare the parent type in the type's `parents`.

**Verify:** `CreateObject` succeeds.

## The name ... is not of a declared type

```text
INVALID_ARGUMENT: The name folders/docs is not of a declared type: its first segment is no type's plural.
```

**Cause:** The first segment of the name is not the `plural` of a declared type, such as a type that the latest
declaration does not have, or a name without a slash.

**Fix:** Name the resource with the plural of its type, such as `projects/ledger`, or declare the type.

**Verify:** `CreateObject` succeeds.

## The id of ... does not match ...

```text
INVALID_ARGUMENT: The id of organizations/Acme Corp does not match [a-z]([a-z0-9-]{0,61}[a-z0-9])?.
```

**Cause:** The id, the last segment of the name, has capitals, spaces or other characters, is longer than 63
characters, or ends with a hyphen.

**Fix:** Give the resource an id of lowercase letters, digits and hyphens that starts with a letter, such as
`acme-corp`, and use the same name in your table.

**Verify:** `CreateObject` succeeds.

## Granting ... on ... to ... makes a cycle

```text
FAILED_PRECONDITION: Granting member on groups/audit to group:finance#member makes a cycle.
```

**Cause:** A grant to a set that already holds the set of the relation on the resource, such as the members of
`groups/finance` as members of `groups/audit` while `groups/audit` is a member of `groups/finance`. A grant to the
resource's own set fails with `to its own group:audit#member makes a cycle.`

**Fix:** Revoke the grant that makes the other direction, or grant the principals of the set one by one.

**Verify:** `CreateGrant` gives the grant, and `Check` of a member answers.

## The type ... is not declared

```text
INVALID_ARGUMENT: The type acme.dev/Folder is not declared.
```

**Cause:** `ListObjects` or `ListChanges` names a type that the latest declaration does not have, or names it by its
singular name or plural instead of its name.

**Fix:** Name the declared type, such as `acme.dev/Project`.

**Verify:** The list gives its page.

## The request names no subject type

```text
INVALID_ARGUMENT: The request names no subject type.
```

**Cause:** `ListSubjects` with an empty `subject_type`.

**Fix:** Name the type of the principals, such as `user`, or a set, such as `group#member`.

**Verify:** `ListSubjects` gives the principals.

## Related

- [About resources](about-resources.md)
- [About subjects and permissions](about-subjects-and-permissions.md)
- [Troubleshooting declarations](../declarations/troubleshooting-declarations.md)
