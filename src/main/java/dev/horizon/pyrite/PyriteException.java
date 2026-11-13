package dev.horizon.pyrite;

import dev.horizon.aquery.InvalidQueryException;
import dev.horizon.cinder.DuplicateKeyException;
import io.vertx.core.Future;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.server.StatusException;
import java.util.EnumSet;
import java.util.Set;

/**
 * A call to one of Pyrite's services that Pyrite refused, with the gRPC status that the call ends with. The message
 * names the resource, the field or the rule, such as {@code The resource projects/ledger is not known.}
 *
 * <p>
 * An interceptor of exchanges fails an exchange with one to end the call with a status of its choice:
 *
 * <pre>{@code
 * pyrite.addExchangeStartInterceptor(context -> {
 *   if (closed.contains(context.definition())) {
 *     context.fail(new PyriteException(GrpcStatus.UNAVAILABLE, "Sign-in is closed."));
 *   } else {
 *     context.next();
 *   }
 * });
 * }</pre>
 *
 * <p>
 * The exception has no stack trace: it reports a request that Pyrite refused, not a fault in Pyrite.
 */
public final class PyriteException extends RuntimeException {

  private static final System.Logger LOGGER = System.getLogger(PyriteException.class.getName());

  private final GrpcStatus status;

  /**
   * Creates the refusal of a call.
   *
   * @param status the status that the call ends with, such as {@code NOT_FOUND}
   * @param message the message that the client gets, which names what was refused and why
   */
  public PyriteException(GrpcStatus status, String message) {
    super(message, null, false, false);
    this.status = status;
  }

  /**
   * Gives the status that the call ends with.
   *
   * @return the status, such as {@code NOT_FOUND}
   */
  public GrpcStatus status() {
    return status;
  }

  /**
   * The statuses of OpenFGA that refuse a request, whose message a caller may read.
   */
  private static final Set<GrpcStatus> CLIENT = EnumSet.of(GrpcStatus.INVALID_ARGUMENT, GrpcStatus.NOT_FOUND,
      GrpcStatus.ALREADY_EXISTS, GrpcStatus.FAILED_PRECONDITION, GrpcStatus.ABORTED, GrpcStatus.PERMISSION_DENIED,
      GrpcStatus.OUT_OF_RANGE);

  /**
   * Ends a call with the gRPC status of its failure, so that a gRPC client sees the status and the message: the status
   * of a {@code PyriteException}, {@code ALREADY_EXISTS} for a duplicate key, {@code INVALID_ARGUMENT} for a filter or
   * an order that is not valid, and the status of a call to OpenFGA that refused the request, such as
   * {@code INVALID_ARGUMENT}. Any other failure, a fault of OpenFGA among them, is a fault, which Pyrite logs, and the
   * call ends with {@code INTERNAL} and no detail.
   *
   * @param failure the failure of the call
   * @param <T> the type of the result of the call
   * @return the future of the call, failed with a {@link StatusException}
   */
  public static <T> Future<T> fail(Throwable failure) {
    return Future.failedFuture(switch (failure) {
      case StatusException status -> status;
      case PyriteException pyrite -> new StatusException(pyrite.status, pyrite.getMessage());
      case InvalidStatusException openFga when CLIENT.contains(openFga.actualStatus()) ->
        new StatusException(openFga.actualStatus(), openFga.getMessage());
      case DuplicateKeyException duplicate -> new StatusException(GrpcStatus.ALREADY_EXISTS, duplicate.getMessage());
      case InvalidQueryException invalid -> new StatusException(GrpcStatus.INVALID_ARGUMENT, invalid.getMessage());
      default -> {
        LOGGER.log(System.Logger.Level.ERROR, "A Pyrite call failed", failure);
        yield new StatusException(GrpcStatus.INTERNAL, "Internal error");
      }
    });
  }
}
