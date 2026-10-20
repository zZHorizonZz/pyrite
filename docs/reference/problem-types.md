# Problem types

Every exception that Pyrite returns, and every exception from the libraries built on it, is a `ProblemException`.
It holds a problem details object of [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457). Its `type` is the URI of its
section on this page. `toJson()` returns it as an `application/problem+json` object.

| Problem type                                  | Class                         | Canonical code        | `status` |
| --------------------------------------------- | ----------------------------- | --------------------- | -------- |
| [`not-found`](#not-found)                     | `NotFoundException`           | `NOT_FOUND`           | `404`    |
| [`already-exists`](#already-exists)           | `AlreadyExistsException`      | `ALREADY_EXISTS`      | `409`    |
| [`aborted`](#aborted)                         | `AbortedException`            | `ABORTED`             | `409`    |
| [`invalid-argument`](#invalid-argument)       | `InvalidArgumentException`    | `INVALID_ARGUMENT`    | `400`    |
| [`failed-precondition`](#failed-precondition) | `FailedPreconditionException` | `FAILED_PRECONDITION` | `400`    |
| [`unimplemented`](#unimplemented)             | `UnimplementedException`      | `UNIMPLEMENTED`       | `501`    |

Every problem has these members of RFC 9457:

| Member     | Type   | Value                                             |
| ---------- | ------ | ------------------------------------------------- |
| `type`     | string | the URI of the problem type                       |
| `title`    | string | the title of the problem type                     |
| `status`   | number | the HTTP status code of the problem type          |
| `detail`   | string | a description of this occurrence                  |
| `instance` | string | the URI of this occurrence, if the caller set one |

To add your own members, use the constructor that takes an `instance` and a `JsonObject`. A member that you add
cannot use the name of a member listed on this page.

## not-found

No resource of the type has the name. The `title` is `Not found`.

| Member         | Type   | Value                                                |
| -------------- | ------ | ---------------------------------------------------- |
| `resourceType` | string | the type of the resource, such as `key` or `version` |
| `resourceName` | string | the name that was requested                          |

## already-exists

A resource of the type already has the name. The `title` is `Already exists`.

| Member         | Type   | Value                    |
| -------------- | ------ | ------------------------ |
| `resourceType` | string | the type of the resource |
| `resourceName` | string | the name that is in use  |

## aborted

Another change updated the resource after the revision that this change named. Read the resource again and retry.
The `title` is `Aborted`.

| Member             | Type   | Value                              |
| ------------------ | ------ | ---------------------------------- |
| `resourceType`     | string | the type of the resource           |
| `resourceName`     | string | the name of the resource           |
| `expectedRevision` | number | the revision that the change named |

## invalid-argument

An argument is not valid, regardless of the state of any resource. The `title` is `Invalid argument`.

| Member             | Type   | Value                                            |
| ------------------ | ------ | ------------------------------------------------ |
| `errors`           | array  | one object for each invalid argument             |
| `errors[].detail`  | string | the rule that the argument breaks                |
| `errors[].pointer` | string | a JSON pointer to the argument, such as `#/name` |

## failed-precondition

The arguments are valid, but the state of a resource does not allow the operation. The `title` is
`Failed precondition`.

| Member   | Type   | Value                                                                |
| -------- | ------ | -------------------------------------------------------------------- |
| `reason` | string | the cause, in upper snake case; each library defines its own reasons |

Pyrite defines these reasons:

| Reason       | Description                                                                          |
| ------------ | ------------------------------------------------------------------------------------ |
| `PURPOSE`    | the key has a different purpose than the one requested                               |
| `UNREADABLE` | the ciphertext was not made by this key under this context, or it was changed        |
| `VERSION`    | the version is in a state that does not allow the operation                          |
| `PRIMARY`    | the operation cannot apply to the primary version, or the key has no primary version |

## unimplemented

The provider does not support the operation, such as a purpose or algorithm that it does not store, or `export()` on
a KMS. The `title` is `Unimplemented`. This problem type has no other members.
