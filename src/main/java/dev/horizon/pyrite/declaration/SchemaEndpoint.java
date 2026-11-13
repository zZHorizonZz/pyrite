package dev.horizon.pyrite.declaration;

import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.deployment.Deployment;
import dev.horizon.pyrite.schema.v1.ReadDeclarationRequest;
import dev.horizon.pyrite.schema.v1.ReadDeclarationResponse;
import dev.horizon.pyrite.schema.v1.SchemaGrpcService;
import dev.horizon.pyrite.schema.v1.WriteDeclarationRequest;
import dev.horizon.pyrite.schema.v1.WriteDeclarationResponse;
import io.vertx.core.Future;

/**
 * The service {@code pyrite.schema.v1.Schema}: the declarations of the types, relations and exchanges of an
 * application.
 *
 * <p>
 * A declaration that breaks a rule fails {@code WriteDeclaration} with {@code INVALID_ARGUMENT}, and one that the
 * options of Pyrite cannot serve with {@code FAILED_PRECONDITION}. {@code ReadDeclaration} fails with
 * {@code NOT_FOUND} for an id that no declaration has, and before the first declaration. Neither method takes a
 * subject.
 */
public final class SchemaEndpoint extends SchemaGrpcService {

  private final Deployment deployment;

  public SchemaEndpoint(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  public Future<WriteDeclarationResponse> writeDeclaration(WriteDeclarationRequest request) {
    return Future.<String> future(promise -> deployment.write(request.getDeclaration()).onComplete(promise))
        .map(id -> WriteDeclarationResponse.newBuilder().setId(id).build()).recover(PyriteException::fail);
  }

  @Override
  public Future<ReadDeclarationResponse> readDeclaration(ReadDeclarationRequest request) {
    return deployment.read(request.getId()).recover(PyriteException::fail);
  }
}
