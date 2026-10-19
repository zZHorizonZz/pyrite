package dev.horizon.pyrite;

/**
 * What a key does, which decides its type.
 *
 * @param <K> the type of a key of this purpose
 */
public sealed interface Purpose<K extends Key> permits Encryption, Mac, Signing {
}
