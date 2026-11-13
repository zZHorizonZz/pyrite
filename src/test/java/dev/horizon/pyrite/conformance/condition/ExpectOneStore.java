package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.cinder.Database;
import dev.horizon.cinder.protobuf.ProtobufExtension;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.schema.declaration.v1.Tables;
import dev.horizon.pyrite.storage.DeclarationBackend;
import java.util.List;

/**
 * Reads the rows of the declarations, and checks that a number of them are stored, one on the other from the first,
 * and that all share one store of grants.
 */
public record ExpectOneStore(int count) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    DeclarationBackend declarations = new DeclarationBackend(Database.builder(environment.pyrite().database.pool(),
        environment.pyrite().database.driver()).extension(new ProtobufExtension()).build());
    List<Tables.Declaration> rows = declarations.list(declarations.query()).await(TIMEOUT);

    assertThat(rows).hasSize(count);
    assertThat(rows).extracting(Tables.Declaration::getStoreId).containsOnly(rows.getFirst().getStoreId());
    assertThat(rows).extracting(Tables.Declaration::getPreviousId).doesNotHaveDuplicates().contains("");
    assertThat(rows.stream().map(Tables.Declaration::getPreviousId).filter(previous -> !previous.isEmpty()))
        .isSubsetOf(rows.stream().map(Tables.Declaration::getId).toList());
  }
}
