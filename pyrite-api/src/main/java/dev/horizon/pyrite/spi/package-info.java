/**
 * What a provider implements to hold keys for a {@link dev.horizon.pyrite.Keyring}.
 * <p>
 * A {@link dev.horizon.pyrite.spi.Provider} stores keys and their versions and runs the operations of a version:
 * encrypt, decrypt, MAC, sign, and its public key. Everything above that is the keyring's: the format of ciphertexts,
 * fingerprints and JWTs, choosing the version, rotating on schedule, publishing, and verifying against public keys.
 * A provider decides nothing about when a version is made or which one is primary; it records what the keyring asks.
 * <p>
 * Every change names the {@link dev.horizon.pyrite.common.Revision} of the key it is made on, and fails with
 * {@link dev.horizon.pyrite.common.AbortedException} once another change has moved it, so that two keyrings over one provider
 * never make two primaries.
 */
package dev.horizon.pyrite.spi;
