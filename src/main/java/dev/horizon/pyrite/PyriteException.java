package dev.horizon.pyrite;

import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.server.StatusException;

/**
 * A failure of a call to one of Pyrite's services, with the gRPC status that the call ends with. The message names the
 * resource, the field or the rule, such as {@code The resource projects/ledger is at revision 2, not 1.}
 *
 * <p>
 * The services end a call that fails this way with {@link #toStatus()}, so a gRPC client sees the status and the
 * message. The exception has no stack trace: it reports a request that Pyrite refused, not a fault in Pyrite.
 */
public final class PyriteException extends RuntimeException {

  private final GrpcStatus status;

  /**
   * Creates the failure.
   *
   * @param status the status that the call ends with, such as {@code NOT_FOUND}
   * @param message the message that the caller sees
   */
  public PyriteException(GrpcStatus status, String message) {
    super(message, null, false, false);
    this.status = status;
  }

  /**
   * Gives the status that the call ends with.
   *
   * @return the status
   */
  public GrpcStatus status() {
    return status;
  }

  /**
   * Gives the failure as the exception that ends a Vert.x gRPC call with its status and message.
   *
   * @return the status exception
   */
  public StatusException toStatus() {
    return new StatusException(status, getMessage());
  }
}
