# Status codes

A failed call of a Pyrite service ends with a gRPC status and a message. The message names the resource, the field or
the rule. The fix of each error is in the troubleshooting page of its area, such as
[Troubleshooting keys](../keys/troubleshooting-keys.md).

## Statuses

| Status                | Means                                                         | Example of the message                                                       |
| --------------------- | ------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| `INVALID_ARGUMENT`    | The request breaks a rule, whatever the data                  | `The id of projects/Ledger does not match [a-z]([a-z0-9-]{0,61}[a-z0-9])?.`  |
| `NOT_FOUND`           | The resource, record, flow or declaration does not exist      | `The resource projects/payroll is not known.`                                |
| `ALREADY_EXISTS`      | A resource has the name, or an account has the identifier     | `The resource projects/ledger is known already.`                             |
| `PERMISSION_DENIED`   | The subject lacks the relation, or an answer is wrong         | `The subject user:bob has no owner on projects/ledger.`                      |
| `FAILED_PRECONDITION` | The request is valid, but the state does not allow it         | `The resource organizations/acme has resources under it; forget them first.` |
| `ABORTED`             | The resource, key, version or flow changed since the revision | `The key keys/payments is at revision 2, not 1.`                             |
| `RESOURCE_EXHAUSTED`  | A flow took all its answers                                   | `The flow flows/9f1c... took 5 answers; start another.`                      |
| `UNAUTHENTICATED`     | A session token does not authenticate                         | `The session has ended.`                                                     |
| `INTERNAL`            | A failure that Pyrite did not expect                          | `Internal error`                                                             |

A status of OpenFGA passes through with its message, such as `INVALID_ARGUMENT` for a tuple that the model does not
allow. An `INTERNAL` status gives no details; Pyrite logs the failure under the logger
`dev.horizon.pyrite.PyriteException`, at the level `ERROR`.

## By call

Each call can also fail with `INVALID_ARGUMENT` and `INTERNAL`.

| Call                            | Other statuses                                                                                                           |
| ------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| `WriteDeclaration`              | `FAILED_PRECONDITION` when the options lack a root key or the options of authentication                                  |
| `ReadDeclaration`               | `NOT_FOUND`                                                                                                              |
| `CreateObject`                  | `NOT_FOUND` for the parent, `PERMISSION_DENIED`, `ALREADY_EXISTS`                                                        |
| `DeleteObject`                  | `NOT_FOUND`, `PERMISSION_DENIED`, `FAILED_PRECONDITION` while resources are under it                                     |
| `ListObjects`                   | None                                                                                                                     |
| `CreateGrant`                   | `NOT_FOUND`, `PERMISSION_DENIED`, `FAILED_PRECONDITION` for a grant that makes a cycle                                   |
| `DeleteGrant`                   | `NOT_FOUND`, `PERMISSION_DENIED`                                                                                         |
| `Check`                         | `NOT_FOUND`, `PERMISSION_DENIED` when asking about another principal                                                     |
| `ListGrants`                    | `NOT_FOUND`, `PERMISSION_DENIED`                                                                                         |
| `ListSubjects`                  | `NOT_FOUND`, `PERMISSION_DENIED`                                                                                         |
| `ListChanges`                   | None                                                                                                                     |
| `CreateKey`                     | `NOT_FOUND` for the parent, `PERMISSION_DENIED`, `ALREADY_EXISTS`                                                        |
| `GetKey`, `ListKeyVersions`     | `NOT_FOUND`, `PERMISSION_DENIED`                                                                                         |
| `UpdateKey`                     | `NOT_FOUND`, `PERMISSION_DENIED`, `ABORTED`                                                                              |
| `RotateKey`                     | `NOT_FOUND`, `PERMISSION_DENIED`                                                                                         |
| `UpdateKeyVersion`              | `NOT_FOUND`, `PERMISSION_DENIED`, `FAILED_PRECONDITION` for the primary version, `ABORTED`                               |
| `DeleteKeyVersion`              | `NOT_FOUND`, `PERMISSION_DENIED`, `FAILED_PRECONDITION` for the primary version                                          |
| `Encrypt`, `Sign`, `Verify`     | `NOT_FOUND`, `PERMISSION_DENIED`, `FAILED_PRECONDITION` for a key of another purpose                                     |
| `Decrypt`                       | `NOT_FOUND`, `PERMISSION_DENIED`, `FAILED_PRECONDITION` for a key of another purpose, or a disabled or destroyed version |
| `CreateAccount`                 | `ALREADY_EXISTS`                                                                                                         |
| `GetAccount`, `ListIdentifiers` | `NOT_FOUND`, `PERMISSION_DENIED`                                                                                         |
| `DeleteAccount`                 | `NOT_FOUND`, `PERMISSION_DENIED`                                                                                         |
| `AddIdentifier`                 | `NOT_FOUND`, `PERMISSION_DENIED`, `ALREADY_EXISTS`                                                                       |
| `GetSession`, `EndSession`      | `NOT_FOUND`, `PERMISSION_DENIED`                                                                                         |
| `Purge`                         | None                                                                                                                     |
| `StartFlow`                     | `NOT_FOUND` for the subject's account or identifier, `FAILED_PRECONDITION`                                               |
| `ContinueFlow`                  | `NOT_FOUND`, `PERMISSION_DENIED`, `FAILED_PRECONDITION`, `ABORTED`, `RESOURCE_EXHAUSTED`                                 |
| `Authenticate`                  | `UNAUTHENTICATED`                                                                                                        |

Before the first declaration is written, `ReadDeclaration` fails with `NOT_FOUND`, and every call of the other
services fails with `FAILED_PRECONDITION`. A call of the cryptography service fails with `FAILED_PRECONDITION` when
the declaration has no `pyrite.dev/Key`, and a call of the authentication service when it has no
`pyrite.dev/Account`.

## Rejections of Verify

`Verify` returns a `Rejected`, with a reason and a message, instead of failing when the signature or the token is
wrong. It checks a token's header first, then its signature, then its claims, and gives the first reason that it
finds:

| Reason             | Means                                                                                                                        |
| ------------------ | ---------------------------------------------------------------------------------------------------------------------------- |
| `MALFORMED`        | The token is not a compact JWS, it names critical header parameters, or its claims are not a JSON object                     |
| `ALGORITHM`        | The token's `alg` is not the key's algorithm, such as `HS256` for an `ES256` key                                             |
| `TYPE`             | The token's `typ` is not the expected type, or `JWT` when the request expects none                                           |
| `UNKNOWN_VERSION`  | The token names no `kid`, or the key has no version of that id or of the `key_version` named; a destroyed version is unknown |
| `DISABLED_VERSION` | The version that the token or the `key_version` names is disabled                                                            |
| `SIGNATURE`        | No version that was tried made the signature                                                                                 |
| `ISSUER`           | The token's `iss` is not the expected issuer                                                                                 |
| `AUDIENCE`         | The token's `aud`, a string or a list, does not hold the expected audience                                                   |
| `EXPIRED`          | The token has no `exp`, or `exp` plus the leeway has passed                                                                  |
| `NOT_YET_VALID`    | The token's `nbf` minus the leeway has not come                                                                              |

Pyrite compares `exp` and `nbf` with the clock of its own server, not of the database. The leeway covers the
difference from the clock of the server that signed the token.

## Related

- [Troubleshooting deployment](troubleshooting-deployment.md)
- [Troubleshooting keys](../keys/troubleshooting-keys.md)
- [Troubleshooting access](../access/troubleshooting-access.md)
- [Troubleshooting accounts](../accounts/troubleshooting-accounts.md)
- [Troubleshooting declarations](../declarations/troubleshooting-declarations.md)
