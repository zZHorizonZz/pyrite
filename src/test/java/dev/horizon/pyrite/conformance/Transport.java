package dev.horizon.pyrite.conformance;

/**
 * How the clients of a run reach Pyrite's services: on the event bus, or as gRPC over HTTP/2.
 */
public enum Transport {
  EVENT_BUS,
  HTTP
}
