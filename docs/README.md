# Pyrite

Pyrite is the identity and access layer of a Vert.x application. Your application declares its types of resources
and their relations, keeps the resources in its own tables, and tells Pyrite when one is created and when it is gone.
Pyrite keeps the grants in OpenFGA and answers who can do what. It also keeps accounts and their sessions, and keys
that encrypt and sign. It serves four gRPC services, schema, access, cryptography and authentication, on PostgreSQL,
MariaDB, SQL Server or SQLite.

To serve Pyrite and check your first permission, see [Get started](get-started.md).

## Areas

| Area                                   | What you do there                                                                                 |
| -------------------------------------- | ------------------------------------------------------------------------------------------------- |
| [Declarations](declarations/README.md) | Declare your types, their parents, relations and permissions, and let users define roles.         |
| [Access](access/README.md)             | Make resources known, grant relations, check permissions and list what a subject can do.          |
| [Accounts](accounts/README.md)         | Keep accounts, sign people in with exchanges, add factors and passkeys, and handle the exchanges. |
| [Keys](keys/README.md)                 | Encrypt data, sign and verify tokens, and rotate keys.                                            |
| [Deployment](deployment/README.md)     | Create the tables, serve the services, set the options, and read the status codes.                |
