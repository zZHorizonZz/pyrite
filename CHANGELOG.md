# Changelog

## [0.3.0](https://github.com/zZHorizonZz/pyrite/compare/v0.2.0...v0.3.0) (2026-10-25)


### ⚠ BREAKING CHANGES

* the DataService and its RPCs are removed, as are the resource fields of a declaration. The IdentifierKind enum is now a string that names a kind of the declaration, so StartFlowRequest, Identifier, AddIdentifierRequest, Delivery and CodeSent carry the kind as a string in new fields. The migrations are replaced by a single one, so a database made by an earlier version is recreated.

### Features

* drop the data service, so an application keeps its own records and Pyrite keeps the identity, access and keys around them. ([0004af2](https://github.com/zZHorizonZz/pyrite/commit/0004af28a70dd7722a1ef700ef1806b4c09f572b))

## [0.2.0](https://github.com/zZHorizonZz/pyrite/compare/v0.1.0...v0.2.0) (2026-10-23)


### ⚠ BREAKING CHANGES

* take the condition context on every request that checks a permission, and store absent values as null.

### Features

* take the condition context on every request that checks a permission, and store absent values as null. ([b0334fe](https://github.com/zZHorizonZz/pyrite/commit/b0334fe25392b61ec02787850c38d37f2561ed49))

## 0.1.0 (2026-10-23)


### Features

* Pyrite, the data layer of a Vert.x application with identity and access built in. ([b4a3b82](https://github.com/zZHorizonZz/pyrite/commit/b4a3b826a13a594bebbc7f50d535a83671d9fa9b))
