package dev.horizon.pyrite;

/**
 * The purpose of a key, which sets the type of the key and its operations.
 *
 * @param <K> the type of a key with this purpose
 */
public sealed interface Purpose<K extends Key> permits Encryption, Mac, Signing {
}
