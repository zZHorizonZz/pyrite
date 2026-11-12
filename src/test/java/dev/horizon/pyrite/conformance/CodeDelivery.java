package dev.horizon.pyrite.conformance;

/**
 * How a run gets the one-time codes that Pyrite sends: in the response of the flow, as Pyrite gives them without an
 * interceptor, or from an interceptor that takes them, as an application's mail sender would.
 */
public enum CodeDelivery {
  RESPONSE,
  INTERCEPTOR
}
