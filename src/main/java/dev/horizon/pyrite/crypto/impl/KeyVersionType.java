package dev.horizon.pyrite.crypto.impl;

import dev.horizon.pyrite.impl.resource.Resource;
import dev.horizon.pyrite.impl.resource.ResourceType;
import dev.horizon.pyrite.schema.v1.Field;
import java.util.List;
import java.util.stream.Stream;

/**
 * The type {@code pyrite.dev/KeyVersion}, beneath keys. Creating a version rotates its key: Pyrite names the version,
 * generates its material and encrypts it by the root key.
 */
public interface KeyVersionType extends ResourceType<KeyVersion> {

  String NUMBER = "number";
  String STATE = "state";
  String ALGORITHM = "algorithm";
  String PUBLIC_KEY = "public_key";
  String SEALED = "sealed";
  String PUBLIC_KEY_INFO = "public_key_info";

  /**
   * The columns of the table {@code pyrite_key_version}.
   */
  List<Field> FIELDS = Stream.concat(Resource.STANDARD.stream(), Stream.of(
      Field.newBuilder().setName(NUMBER).setKind(Field.Kind.INTEGER).build(),
      Field.newBuilder().setName(STATE).setKind(Field.Kind.STRING).build(),
      Field.newBuilder().setName(ALGORITHM).setKind(Field.Kind.STRING).build(),
      Field.newBuilder().setName(PUBLIC_KEY).setKind(Field.Kind.MAP).build(),
      Field.newBuilder().setName(SEALED).setKind(Field.Kind.BYTES).build(),
      Field.newBuilder().setName(PUBLIC_KEY_INFO).setKind(Field.Kind.BYTES).build())).toList();

}
