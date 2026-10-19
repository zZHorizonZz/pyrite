/**
 * The provider that keeps keys in a PostgreSQL, MariaDB, SQL Server or SQLite database, through Cinder.
 * <p>
 * {@link dev.horizon.pyrite.database.DatabaseProvider#open} opens the provider on a pool. The migrations under
 * {@code pyrite/migration} in the JAR create its three tables:
 * <ul>
 * <li>{@code pyrite_vault} holds the vault key, encrypted under the root key, a Tink {@code Aead}. The provider makes
 * the vault key the first time it opens on a database.</li>
 * <li>{@code pyrite_key} holds each key: its purpose, its rotation and its revision.</li>
 * <li>{@code pyrite_version} holds each version, numbered from 1 in its key: its state, its origin, its public key, its
 * certificate, and its secret material, encrypted under the vault key and bound to the key and the number.</li>
 * </ul>
 * <p>
 * A key is the only way to change it and its versions. Each change advances the key's revision from the one it names
 * and makes the change in the same transaction; a change that names an older revision fails with
 * {@link dev.horizon.pyrite.common.AbortedException}. A new version takes the number after the key's latest one inside
 * that transaction, so two writers never take the same number.
 * <p>
 * The vault unseals the material of a version with the vault key when it is first used, and holds it for an hour
 * after its last use, at most 10,000 versions at a time. A change of a version's state lets go of it.
 * <p>
 * To rotate the root key, add a key to the root keyset and make it primary, call
 * {@link dev.horizon.pyrite.database.DatabaseProvider#rewrap()}, which encrypts the vault key again under it, and then
 * remove the old key from the root keyset. The versions are not touched.
 */
package dev.horizon.pyrite.database;
