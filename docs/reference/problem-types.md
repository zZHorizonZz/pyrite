# Problem types

Every failure of Pyrite, and of each library built on it, is a `ProblemException`: a problem details object of
[RFC 9457](https://www.rfc-editor.org/rfc/rfc9457). Its `type` is the URI of its section on this page, and
`toJson()` writes it as `application/problem+json`.

| Problem type | Class | Canonical code | `status` |
| --- | --- | --- | --- |
| [`not-found`](#not-found) | `NotFoundException` | `NOT_FOUND` | `404` |
| [`already-exists`](#already-exists) | `AlreadyExistsException` | `ALREADY_EXISTS` | `409` |
| [`aborted`](#aborted) | `AbortedException` | `ABORTED` | `409` |
| [`invalid-argument`](#invalid-argument) | `InvalidArgumentException` | `INVALID_ARGUMENT` | `400` |
| [`failed-precondition`](#failed-precondition) | `FailedPreconditionException` | `FAILED_PRECONDITION` | `400` |
| [`unimplemented`](#unimplemented) | `UnimplementedException` | `UNIMPLEMENTED` | `501` |

Every problem has the members of RFC 9457:

| Member | Type | Value |
| --- | --- | --- |
| `type` | string | the URI of the problem type |
| `title` | string | the title of the problem type, listed below |
| `status` | number | the HTTP status code of the problem type, listed above |
| `detail` | string | what went wrong in this occurrence |
| `instance` | string | the URI of this occurrence; only when the caller gave one |

A caller can declare more extension members through the constructor that takes an `instance` and a `JsonObject`. A
declared member cannot have the name of a member on this page.

## not-found

Nothing of that type has that name. `title` is `Not found`.

| Member | Type | Value |
| --- | --- | --- |
| `resourceType` | string | the type of what was asked for, such as `key` or `version` |
| `resourceName` | string | the name it was asked for by |

## already-exists

Something of that type already has that name. `title` is `Already exists`.

| Member | Type | Value |
| --- | --- | --- |
| `resourceType` | string | the type of what was to be created |
| `resourceName` | string | the name that is taken |

## aborted

Another write moved the revision that this write named. `title` is `Aborted`.

| Member | Type | Value |
| --- | --- | --- |
| `resourceType` | string | the type of what was written |
| `resourceName` | string | its name |
| `expectedRevision` | number | the revision that the write named |

## invalid-argument

The input breaks a rule, whatever the state. `title` is `Invalid argument`.

| Member | Type | Value |
| --- | --- | --- |
| `errors` | array | one object for each field that breaks a rule |
| `errors[].detail` | string | the rule the field breaks |
| `errors[].pointer` | string | a JSON pointer to the field, such as `#/name` |

## failed-precondition

The input is valid, and the state does not allow it. `title` is `Failed precondition`.

| Member | Type | Value |
| --- | --- | --- |
| `reason` | string | what does not allow it, in upper snake case; each library lists its own reasons |

Pyrite's reasons:

| Reason | Means |
| --- | --- |
| `PURPOSE` | the key has another purpose than the one it was asked for as |
| `UNREADABLE` | the ciphertext was not made by this key under that context, or was changed |
| `VERSION` | the version is not in a state that allows it |
| `PRIMARY` | the version is primary, and a rotation has to replace it first |

## unimplemented

The provider does not do what was asked. Examples are a purpose or an algorithm it does not hold, or `export()` on a
cloud KMS. `title` is `Unimplemented`. This problem type has no extension members.
