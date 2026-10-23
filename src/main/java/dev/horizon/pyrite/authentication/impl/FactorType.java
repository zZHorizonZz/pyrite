package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.impl.resource.Resource;
import dev.horizon.pyrite.impl.resource.ResourceType;
import dev.horizon.pyrite.schema.v1.Field;
import java.util.List;
import java.util.stream.Stream;

/**
 * The type {@code pyrite.dev/Factor}, beneath accounts. Flows enrol its factors, and a request cannot create one. Their
 * secrets are hashed, encrypted by the encryption key of the options, or kept as MACs.
 */
public interface FactorType extends ResourceType<Factor> {

  String KIND = "kind";
  String STATE = "state";
  String SECRET = "secret";
  String CREDENTIAL_ID = "credential_id";
  String COUNTER = "counter";
  String USER_VERIFIED = "user_verified";

  /**
   * The columns of the table {@code pyrite_factor}.
   */
  List<Field> FIELDS = Stream.concat(Resource.STANDARD.stream(), Stream.of(
      Field.newBuilder().setName(KIND).setKind(Field.Kind.STRING).build(),
      Field.newBuilder().setName(STATE).setKind(Field.Kind.STRING).build(),
      Field.newBuilder().setName(SECRET).setKind(Field.Kind.BYTES).build(),
      Field.newBuilder().setName(CREDENTIAL_ID).setKind(Field.Kind.STRING).build(),
      Field.newBuilder().setName(COUNTER).setKind(Field.Kind.INTEGER).build(),
      Field.newBuilder().setName(USER_VERIFIED).setKind(Field.Kind.BOOLEAN).build())).toList();

}
