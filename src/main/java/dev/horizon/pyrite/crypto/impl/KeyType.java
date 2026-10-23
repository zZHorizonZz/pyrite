package dev.horizon.pyrite.crypto.impl;

import dev.horizon.pyrite.impl.resource.Resource;
import dev.horizon.pyrite.impl.resource.ResourceType;
import dev.horizon.pyrite.schema.v1.Field;
import io.vertx.core.Future;
import java.util.List;
import java.util.stream.Stream;

/**
 * The type {@code pyrite.dev/Key}, which creates keys with their first version and reads them by name.
 *
 * <p>
 * A key's purpose and algorithm are set at creation and never change.
 */
public interface KeyType extends ResourceType<Key> {

  String PURPOSE = "purpose";
  String ALGORITHM = "algorithm";
  String ROTATION_PERIOD = "rotation_period";
  String NEXT_ROTATION_TIME = "next_rotation_time";
  String PRIMARY_VERSION = "primary_version";

  /**
   * The relations that Pyrite checks on a key, which the declaration of {@code pyrite.dev/Key} must have.
   */
  List<String> RELATIONS = List.of("viewer", "admin", "encrypter", "decrypter", "signer", "verifier");

  /**
   * The columns of the table {@code pyrite_key}.
   */
  List<Field> FIELDS = Stream.concat(Resource.STANDARD.stream(), Stream.of(
      Field.newBuilder().setName(PURPOSE).setKind(Field.Kind.STRING).build(),
      Field.newBuilder().setName(ALGORITHM).setKind(Field.Kind.STRING).build(),
      Field.newBuilder().setName(ROTATION_PERIOD).setKind(Field.Kind.INTEGER).build(),
      Field.newBuilder().setName(NEXT_ROTATION_TIME).setKind(Field.Kind.TIMESTAMP).build(),
      Field.newBuilder().setName(PRIMARY_VERSION).setKind(Field.Kind.STRING).build())).toList();

  /**
   * Reads an active key.
   *
   * @param name the key's name, such as {@code keys/payments}
   * @return the key, or a failure with {@code NOT_FOUND} or {@code FAILED_PRECONDITION} when it is deleted
   */
  Future<Key> key(String name);

  /**
   * Reads an active encryption key.
   *
   * @param name the key's name
   * @return the key, or a failure with {@code FAILED_PRECONDITION} for a key of another purpose
   */
  Future<EncryptionKey> encryption(String name);

  /**
   * Reads an active MAC or signing key.
   *
   * @param name the key's name
   * @return the key, or a failure with {@code FAILED_PRECONDITION} for an encryption key
   */
  Future<SigningKey> signing(String name);
}
