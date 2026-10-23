package dev.horizon.pyrite.impl.resource;

import dev.horizon.pyrite.schema.v1.Declaration;
import dev.horizon.pyrite.schema.v1.ReadDeclarationResponse;
import io.vertx.core.Future;

/**
 * The declarations of one database: the latest one, which every call reads, and the writing of new ones.
 *
 * <p>
 * Each service holds one deployment. Every call reads the latest declaration's id from the table
 * {@code pyrite_declaration}, so a declaration that one service writes takes effect at the next call of every service.
 */
public interface Deployment {

  /**
   * Gives the schema of the latest declaration. The schema of each declaration is built once and kept.
   *
   * @return the schema, or a failure with {@code FAILED_PRECONDITION} when no declaration is written
   */
  Future<Schema> schema();

  /**
   * Checks a declaration, writes its authorization model to OpenFGA, and makes it the latest. The first write creates
   * the OpenFGA store {@code pyrite}.
   *
   * @param declaration the declaration
   * @return the declaration's id, or a failure with {@code INVALID_ARGUMENT} for a rule that it breaks
   */
  Future<String> write(Declaration declaration);

  /**
   * Reads a declaration.
   *
   * @param id the declaration's id; empty for the latest
   * @return the declaration, its id and when it was written, or a failure with {@code NOT_FOUND}
   */
  Future<ReadDeclarationResponse> read(String id);
}
