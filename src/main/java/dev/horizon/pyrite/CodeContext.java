package dev.horizon.pyrite;

import dev.horizon.pyrite.authentication.v1.Delivery;

/**
 * What an interceptor of a one-time code sees: the code, and the identifier that it goes to. The interceptors deliver
 * it, such as by email; the response of the exchange then says that a code was sent, but not the code.
 *
 * <pre>{@code
 * pyrite.addCodeInterceptor(context -> {
 *   Delivery delivery = context.delivery();
 *   mailer.send(delivery.getTo(), delivery.getCode()).onSuccess(sent -> context.next()).onFailure(context::fail);
 * });
 * }</pre>
 *
 * <p>
 * An interceptor calls {@link #next()} or {@link #fail(Throwable)}, now or later. A {@link PyriteException} fails the
 * call with its status, and an exception that Pyrite does not expect, such as an {@code IOException}, fails it with
 * {@code INTERNAL}.
 */
public interface CodeContext {

  /**
   * Gives the code and where it goes.
   *
   * @return the delivery: the code, its expiry, and the value, the kind and the name of the identifier
   */
  Delivery delivery();

  /**
   * Passes the code on to the next interceptor, or on with the exchange after the last one.
   */
  void next();

  /**
   * Stops the exchange, such as when the code could not be sent.
   *
   * @param cause the failure that the call ends with
   */
  void fail(Throwable cause);
}
