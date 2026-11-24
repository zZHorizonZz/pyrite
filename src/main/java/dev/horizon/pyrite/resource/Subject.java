package dev.horizon.pyrite.resource;

import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.declaration.TypeSystem;
import io.vertx.grpc.common.GrpcStatus;
import java.nio.charset.StandardCharsets;

/**
 * The principal that a call runs as, or that a check asks about, such as {@code pyrite.dev/Account:accounts/anne}.
 * Pyrite does not verify it: the caller sets it from a credential that it verified.
 *
 * @param type the OpenFGA type, such as {@code pyrite.dev/Account}
 * @param id the id, such as {@code accounts/anne}
 */
public record Subject(String type, String id) {

  /** The most bytes of a subject in UTF-8, as OpenFGA takes a user. */
  public static final int MAX_BYTES = 512;

  /**
   * Reads a principal.
   *
   * @param text the principal, such as {@code pyrite.dev/Account:accounts/anne}
   * @return the principal
   * @throws PyriteException with {@code INVALID_ARGUMENT} for text that is not {@code type:id}, with a type that
   * matches {@link TypeSystem#TYPE_NAME} and an id other than {@code *} without {@code :}, {@code #}, white space or
   * control characters, in at most {@value #MAX_BYTES} bytes
   */
  public static Subject parse(String text) {
    return ObjectRef.split(text).filter(object -> text.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES)
        .map(object -> new Subject(object.type(), object.id()))
        .orElseThrow(() -> new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The subject '" + text + "' is not a"
            + " principal of the form type:id, such as " + TypeSystem.ACCOUNT + ":accounts/anne."));
  }

  /**
   * Gives the subject that an account is: the account's resource.
   *
   * @param account the name of the account, such as {@code accounts/anne}
   * @return the subject, such as {@code pyrite.dev/Account:accounts/anne}
   */
  public static Subject account(String account) {
    return new Subject(TypeSystem.ACCOUNT, account);
  }

  @Override
  public String toString() {
    return type + ":" + id;
  }
}
