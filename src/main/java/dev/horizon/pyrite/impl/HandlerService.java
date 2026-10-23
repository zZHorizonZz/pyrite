package dev.horizon.pyrite.impl;

import com.google.protobuf.Descriptors;
import io.vertx.core.Handler;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class HandlerService implements Service {

  private final ServiceName name;
  private final Descriptors.ServiceDescriptor descriptor;
  private final List<ServiceMethod<?, ?>> methods = new ArrayList<>();
  private final Map<String, Handler<?>> handlers = new HashMap<>();

  protected HandlerService(ServiceName name, Descriptors.ServiceDescriptor descriptor) {
    this.name = name;
    this.descriptor = descriptor;
  }

  protected <Req, Resp> void bind(ServiceMethod<Req, Resp> method, Handler<GrpcServerRequest<Req, Resp>> handler) {
    methods.add(method);
    handlers.put(method.methodName(), handler);
  }

  @Override
  public ServiceName name() {
    return name;
  }

  @Override
  public Descriptors.ServiceDescriptor descriptor() {
    return descriptor;
  }

  @Override
  public List<ServiceMethod<?, ?>> methods() {
    return List.copyOf(methods);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <Req, Resp> Handler<GrpcServerRequest<Req, Resp>> handler(ServiceMethod<Req, Resp> method) {
    Handler<?> handler = handlers.get(method.methodName());
    return handler == null ? Service.super.handler(method) : (Handler<GrpcServerRequest<Req, Resp>>) handler;
  }
}
