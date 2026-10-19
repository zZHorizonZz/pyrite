/**
 * What the libraries built on Pyrite share, so that a host or a dialect maps it once.
 * <p>
 * A {@link dev.horizon.pyrite.common.Subject} is who a call is about, in the shape of AuthZEN's subject: a type, an id
 * and properties. The types are open; each dialect brings its own, such as {@code user}, {@code serviceAccount} or
 * {@code principalSet}.
 * <p>
 * A {@link dev.horizon.pyrite.common.Revision} counts the changes of one stored thing; a write that names the revision
 * it read fails with {@link dev.horizon.pyrite.common.AbortedException} once another write has moved it.
 * <p>
 * Every problem a library reports is a {@link dev.horizon.pyrite.common.ProblemException}, a problem details object of
 * RFC 9457 that writes itself as {@code application/problem+json}, one class for each canonical code:
 * <ul>
 * <li>{@link dev.horizon.pyrite.common.NotFoundException}: nothing of that type has that name;</li>
 * <li>{@link dev.horizon.pyrite.common.AlreadyExistsException}: something of that type already has that name;</li>
 * <li>{@link dev.horizon.pyrite.common.AbortedException}: the revision a write named is no longer the current one;</li>
 * <li>{@link dev.horizon.pyrite.common.InvalidArgumentException}: the input breaks a rule, with one violation per
 * field;</li>
 * <li>{@link dev.horizon.pyrite.common.FailedPreconditionException}: the input is valid and the state does not allow
 * it, with a reason in upper snake case that a caller can match on;</li>
 * <li>{@link dev.horizon.pyrite.common.UnimplementedException}: what was asked is not something this implementation
 * does at all.</li>
 * </ul>
 */
package dev.horizon.pyrite.common;
