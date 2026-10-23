package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.impl.resource.Resource;
import dev.horizon.pyrite.impl.resource.ResourceType;
import dev.horizon.pyrite.impl.resource.Subject;
import dev.horizon.pyrite.schema.v1.Field;
import io.vertx.core.Future;
import java.util.List;
import java.util.stream.Stream;

/**
 * The type {@code pyrite.dev/Account}. When it creates an account, it grants the account the relation {@value #OWNER}
 * on itself.
 */
public interface AccountType extends ResourceType<Account> {

  String KIND = "kind";
  String OWNER = "owner";

  /**
   * The kinds of account.
   */
  List<String> KINDS = List.of("HUMAN", "SERVICE");

  /**
   * The relations that Pyrite checks on an account, which the declaration of {@code pyrite.dev/Account} must have.
   */
  List<String> RELATIONS = List.of(OWNER, "viewer", "admin");

  /**
   * The columns of the table {@code pyrite_account}.
   */
  List<Field> FIELDS = Stream.concat(Resource.STANDARD.stream(),
      Stream.of(Field.newBuilder().setName(KIND).setKind(Field.Kind.STRING).build())).toList();

  /**
   * Reads the active account of a principal.
   *
   * @param subject the principal, such as {@code user:anne}
   * @return the account, or a failure with {@code INVALID_ARGUMENT} for a principal of another type than
   * {@code user}
   */
  Future<Account> account(Subject subject);
}
