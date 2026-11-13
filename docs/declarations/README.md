# Declarations

A declaration tells Pyrite about your application: the types of its resources, where they sit, who may do what on
them, the conditions that grants can carry, the kinds of identifiers that accounts have, and the exchanges that accounts
go through. It is a `pyrite.schema.v1.Declaration` message, which you write with `WriteDeclaration` of the schema
service. Every call of every service reads the latest declaration.

## Features

**Types and their hierarchy.** Declare a type of resource with its parents, its OpenFGA relations and the relations
that Pyrite checks, so that a grant on an organization reaches its projects. See [Declare a type](declare-a-type.md).

**Conditions on grants.** Let a relation take grants that hold only while a CEL condition is true, such as until an
expiry time. See [Declare a type](declare-a-type.md#add-a-condition-to-a-grant).

**Roles that users define.** Let the administrators of your application make their own roles at runtime, from
permissions that the declaration fixes. See [Let users define roles](let-users-define-roles.md).

**Pyrite's own types.** Declare `pyrite.dev/Key` and `pyrite.dev/Account`, with the relations that the cryptography
and authentication services check. See [Pyrite's types](pyrite-types.md).

**Identifier kinds and exchanges.** Declare what people sign in with, such as an email address, and the exchanges that
sign them in, verify an identifier or enrol a factor. See [Declaration reference](declaration-reference.md).

**One declaration at a time.** Each write checks the whole declaration, writes its authorization model to OpenFGA and
becomes the latest, which every service reads at its next call. See [About declarations](about-declarations.md).

`WriteDeclaration` refuses a declaration that breaks a rule with `INVALID_ARGUMENT`, and the message names the type,
the relation or the exchange and the rule. The errors of declarations are in
[Troubleshooting declarations](troubleshooting-declarations.md).

## Related

- [Access](../access/README.md)
- [Accounts](../accounts/README.md)
- [Keys](../keys/README.md)
- [OpenFGA: Concepts](https://openfga.dev/docs/concepts)
