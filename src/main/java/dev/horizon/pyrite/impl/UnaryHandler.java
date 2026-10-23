package dev.horizon.pyrite.impl;

import com.google.protobuf.Message;
import dev.horizon.aquery.InvalidQueryException;
import dev.horizon.cinder.DuplicateKeyException;
import dev.horizon.pyrite.PyriteException;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.StatusException;

public abstract class UnaryHandler<Req extends Message, Resp extends Message>
    implements Handler<GrpcServerRequest<Req, Resp>> {

  public static final ServiceName DATA = ServiceName.create("pyrite.data.v1", "Data");
  public static final ServiceName ACCESS = ServiceName.create("pyrite.access.v1", "Access");
  public static final ServiceName CRYPTOGRAPHY = ServiceName.create("pyrite.crypto.v1", "Cryptography");
  public static final ServiceName SCHEMA = ServiceName.create("pyrite.schema.v1", "Schema");
  public static final ServiceName AUTHENTICATION = ServiceName.create("pyrite.authentication.v1", "Authentication");

  private static final System.Logger LOGGER = System.getLogger(UnaryHandler.class.getName());

  protected static <Req extends Message, Resp extends Message> ServiceMethod<Req, Resp> method(ServiceName service,
      String name, Req prototype) {
    return ServiceMethod.server(service, name, GrpcMessageEncoder.encoder(), GrpcMessageDecoder.decoder(prototype));
  }

  @Override
  public void handle(GrpcServerRequest<Req, Resp> request) {
    request.handler(message -> call(message).onComplete(done -> {
      if (done.succeeded()) {
        request.response().end(done.result());
      } else {
        request.response().fail(done.cause());
      }
    }));
  }

  protected abstract Future<Resp> execute(Req request);

  private Future<Resp> call(Req request) {
    Future<Resp> result;
    try {
      result = execute(request);
    } catch (RuntimeException failed) {
      result = Future.failedFuture(failed);
    }
    return result.recover(failed -> Future.failedFuture(status(failed)));
  }

  private static StatusException status(Throwable failure) {
    return switch (failure) {
      case StatusException status -> status;
      case PyriteException pyrite -> pyrite.toStatus();
      case InvalidStatusException spectrum -> new StatusException(spectrum.actualStatus(), spectrum.getMessage());
      case DuplicateKeyException duplicate -> new StatusException(GrpcStatus.ALREADY_EXISTS, duplicate.getMessage());
      case InvalidQueryException invalid -> new StatusException(GrpcStatus.INVALID_ARGUMENT, invalid.getMessage());
      default -> {
        LOGGER.log(System.Logger.Level.ERROR, "A Pyrite call failed", failure);
        yield new StatusException(GrpcStatus.INTERNAL, "Internal error");
      }
    };
  }
}
