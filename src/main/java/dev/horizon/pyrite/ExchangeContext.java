package dev.horizon.pyrite;

import java.util.Optional;

/**
 * What an interceptor of an exchange sees: the exchange and its account. Each interceptor calls {@link #next()} to pass
 * the exchange on to the next one, or {@link #fail(Throwable)} to stop it, now or later, such as when a future of its
 * own completes.
 *
 * <pre>{@code
 * pyrite.addExchangeEndInterceptor(context -> {
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
public interface ExchangeContext {

  /**
   * Gives the name of the exchange.
   *
   * @return the name, such as {@code exchanges/3f2a}
   */
  String exchange();

  /**
   * Gives the name of the declared definition of the exchange.
   *
   * @return the name, such as {@code sign-in}
   */
  String definition();

  /**
   * Gives the account of the exchange.
   *
   * @return the name of the account, such as {@code accounts/anne}, or nothing for an exchange that names no account
   * that exists
   */
  Optional<String> account();

  /**
   * Passes the exchange on to the next interceptor, or on with the exchange after the last one.
   */
  void next();

  /**
   * Stops the exchange, and fails the call.
   *
   * @param cause the failure that the call ends with, such as a {@link PyriteException} with its status
   */
  void fail(Throwable cause);
}
