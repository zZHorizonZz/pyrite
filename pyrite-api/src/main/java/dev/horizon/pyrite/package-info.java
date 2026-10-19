/**
 * The keys of an application, in the model of Cloud KMS and AWS KMS.
 * <p>
 * A {@link dev.horizon.pyrite.Keyring} holds keys through one {@link dev.horizon.pyrite.spi.Provider}. A
 * {@link dev.horizon.pyrite.Key} has a name, one {@link dev.horizon.pyrite.Purpose} and a
 * {@link dev.horizon.pyrite.Rotation}; its purpose decides its type: an {@link dev.horizon.pyrite.EncryptionKey}, a
 * {@link dev.horizon.pyrite.MacKey} or a {@link dev.horizon.pyrite.SigningKey}. A key has
 * {@link dev.horizon.pyrite.Version versions}: one primary, which encrypts, fingerprints or signs, and older ones, which
 * still decrypt, match and verify until they are disabled or destroyed.
 * <p>
 * What a version makes names that version, so a rotation needs no migration of what was stored.
 * <p>
 * Every call answers a Vert.x {@link io.vertx.core.Future}, because a provider may be a remote service. A call fails
 * with a {@link dev.horizon.pyrite.common.ProblemException}:
 * <ul>
 * <li>{@link dev.horizon.pyrite.common.NotFoundException} for a key or a version that does not exist;</li>
 * <li>{@link dev.horizon.pyrite.common.AlreadyExistsException} for a name that is taken;</li>
 * <li>{@link dev.horizon.pyrite.common.InvalidArgumentException} for input that breaks a rule;</li>
 * <li>{@link dev.horizon.pyrite.common.AbortedException} for a write that lost to another;</li>
 * <li>{@link dev.horizon.pyrite.common.UnimplementedException} for what the provider does not do at all, such as a
 * purpose, an algorithm, or exporting a private key from a cloud KMS;</li>
 * <li>{@link dev.horizon.pyrite.common.FailedPreconditionException} for what the state does not allow, with the name
 * of a {@link dev.horizon.pyrite.Reason} as its reason:
 * <ul>
 * <li>{@code PURPOSE}: the key has another purpose than the one it was asked for as;</li>
 * <li>{@code UNREADABLE}: the ciphertext was not made by this key under that context, or was changed;</li>
 * <li>{@code VERSION}: the version is not in a state that allows it;</li>
 * <li>{@code PRIMARY}: the version is primary, and a rotation has to replace it first.</li>
 * </ul>
 * </li>
 * </ul>
 */
package dev.horizon.pyrite;
