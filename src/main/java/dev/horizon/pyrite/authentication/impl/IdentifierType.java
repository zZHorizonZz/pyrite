package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.authentication.v1.IdentifierKind;
import dev.horizon.pyrite.impl.resource.Resource;
import dev.horizon.pyrite.impl.resource.ResourceType;
import dev.horizon.pyrite.schema.v1.Field;
import io.vertx.core.Future;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The type {@code pyrite.dev/Identifier}, beneath accounts. It checks the format of each kind, and stores email
 * addresses and usernames trimmed and lowercased, and phone numbers without spaces and hyphens.
 */
public interface IdentifierType extends ResourceType<Identifier> {

  String KIND = "kind";
  String VALUE = "value";
  String VERIFIED = "verified";
  String PRIMARY = "primary";

  /**
   * The columns of the table {@code pyrite_identifier}.
   */
  List<Field> FIELDS = Stream.concat(Resource.STANDARD.stream(), Stream.of(
      Field.newBuilder().setName(KIND).setKind(Field.Kind.STRING).build(),
      Field.newBuilder().setName(VALUE).setKind(Field.Kind.STRING).build(),
      Field.newBuilder().setName(VERIFIED).setKind(Field.Kind.BOOLEAN).build(),
      Field.newBuilder().setName(PRIMARY).setKind(Field.Kind.BOOLEAN).build())).toList();

  /**
   * Finds the active identifier of a kind and a value.
   *
   * @param kind the kind
   * @param value the value, which is normalized before it is looked up
   * @return the identifier, or empty when no account has it
   */
  Future<Optional<Identifier>> find(IdentifierKind kind, String value);
}
