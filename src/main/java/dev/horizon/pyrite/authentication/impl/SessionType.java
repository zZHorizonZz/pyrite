package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.authentication.v1.Principal;
import dev.horizon.pyrite.impl.resource.Resource;
import dev.horizon.pyrite.impl.resource.ResourceType;
import dev.horizon.pyrite.schema.v1.Field;
import io.vertx.core.Future;
import java.util.List;
import java.util.stream.Stream;

/**
 * The type {@code pyrite.dev/Session}, beneath accounts. Flows issue its sessions, and a request cannot create one.
 */
public interface SessionType extends ResourceType<Session> {

  String TOKEN_MAC = "token_mac";
  String EXPIRE_TIME = "expire_time";
  String IDLE_EXPIRE_TIME = "idle_expire_time";

  /**
   * The columns of the table {@code pyrite_session}.
   */
  List<Field> FIELDS = Stream.concat(Resource.STANDARD.stream(), Stream.of(
      Field.newBuilder().setName(TOKEN_MAC).setKind(Field.Kind.BYTES).build(),
      Field.newBuilder().setName(EXPIRE_TIME).setKind(Field.Kind.TIMESTAMP).build(),
      Field.newBuilder().setName(IDLE_EXPIRE_TIME).setKind(Field.Kind.TIMESTAMP).build())).toList();

  /**
   * Authenticates a session token.
   *
   * @param token the token, {@code <id>.<secret>}
   * @return the principal of the session's account, or a failure with {@code UNAUTHENTICATED}
   */
  Future<Principal> authenticate(String token);
}
