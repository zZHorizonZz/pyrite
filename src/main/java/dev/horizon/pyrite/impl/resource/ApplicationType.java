package dev.horizon.pyrite.impl.resource;

import dev.horizon.cinder.Database;
import dev.horizon.pyrite.impl.model.DeclaredType;
import dev.horizon.pyrite.impl.storage.Row;
import java.util.Set;

final class ApplicationType extends BaseResourceType<Resource> {

  ApplicationType(DeclaredType declared, SchemaImpl schema, Database database) {
    super(declared, schema, database, Set.of(), Set.of(), Set.of(), false);
  }

  @Override
  protected Resource resource(Row row) {
    return new ApplicationResource(this, row);
  }
}
