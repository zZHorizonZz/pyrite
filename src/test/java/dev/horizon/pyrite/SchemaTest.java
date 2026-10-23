package dev.horizon.pyrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.horizon.pyrite.schema.v1.Declaration;
import dev.horizon.pyrite.schema.v1.ReadDeclarationRequest;
import dev.horizon.pyrite.schema.v1.Type;
import dev.horizon.pyrite.schema.v1.WriteDeclarationRequest;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchemaTest {

  private Installation installation;

  @BeforeEach
  void start() throws Exception {
    installation = Installation.start(Instant.parse("2026-01-01T00:00:00Z"));
  }

  @AfterEach
  void stop() throws Exception {
    installation.close();
  }

  @Test
  void aDeclarationThatNamesAnUndeclaredParentIsRefused() throws Exception {
    Declaration acme = Acme.declaration();
    Type project = acme.getTypesList().stream().filter(type -> type.getName().equals("acme.dev/Project"))
        .findFirst().orElseThrow();
    Declaration broken = acme.toBuilder().setTypes(acme.getTypesList().indexOf(project), project.toBuilder()
        .setParents(0, "acme.dev/Folder")).build();

    assertThatThrownBy(() -> installation.schema.writeDeclaration(WriteDeclarationRequest.newBuilder()
        .setDeclaration(broken).build()).await(10, TimeUnit.SECONDS))
        .isInstanceOfSatisfying(InvalidStatusException.class,
            failure -> assertThat(failure.actualStatus()).isEqualTo(GrpcStatus.INVALID_ARGUMENT));
  }

  @Test
  void theLatestDeclarationIsTheOneThatEveryServiceReads() throws Exception {
    installation.clock.move(Duration.ofSeconds(1));
    Declaration acme = Acme.declaration();
    String id = installation.schema.writeDeclaration(WriteDeclarationRequest.newBuilder().setDeclaration(acme)
        .build()).await(10, TimeUnit.SECONDS).getId();

    assertThat(installation.schema.readDeclaration(ReadDeclarationRequest.getDefaultInstance())
        .await(10, TimeUnit.SECONDS).getId()).isEqualTo(id);
  }
}
