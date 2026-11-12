package dev.horizon.pyrite;

import dev.horizon.pyrite.authentication.v1.Result;
import java.util.Optional;

/**
 * What an interceptor of a flow sees: the flow, its account, and the result that the flow gives back when it ends.
 * Each interceptor calls {@link #next()} to pass the flow on to the next one, or {@link #fail(Throwable)} to stop it,
 * now or later, such as when a future of its own completes.
 *
 * <pre>{@code
 * pyrite.addFlowEndInterceptor(context -> {
 *   if (context.definition().equals("sign-in")) {
 *     audit.signedIn(context.account().orElseThrow());
 *   }
 *   context.next();
 * });
 * }</pre>
 *
 * <p>
 * A {@link PyriteException} fails the call with its status, and an exception that Pyrite does not expect, such as an
 * {@code IOException}, fails it with {@code INTERNAL}. An exception that the interceptor throws fails the call as a
 * cause does. An interceptor that calls neither method leaves the call without an answer.
 */
public interface FlowContext {

  /**
   * Gives the name of the flow.
   *
   * @return the name, such as {@code flows/3f2a}
   */
  String flow();

  /**
   * Gives the name of the declared definition of the flow.
   *
   * @return the name, such as {@code sign-in}
   */
  String definition();

  /**
   * Gives the account of the flow.
   *
   * @return the name of the account, such as {@code accounts/anne}, or nothing for a flow that names no account that
   * exists
   */
  Optional<String> account();

  /**
   * Gives the result that the flow gives back when it ends, which the interceptors of its end fill in.
   *
   * @return the result so far, which the interceptors can change
   */
  Result.Builder result();

  /**
   * Passes the flow on to the next interceptor, or on with the flow after the last one.
   */
  void next();

  /**
   * Stops the flow, and fails the call.
   *
   * @param cause the failure that the call ends with, such as a {@link PyriteException} with its status
   */
  void fail(Throwable cause);
}
