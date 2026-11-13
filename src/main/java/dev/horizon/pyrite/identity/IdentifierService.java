package dev.horizon.pyrite.identity;

import static dev.horizon.basepoint.filter.Filter.and;
import static dev.horizon.basepoint.filter.Filter.eq;

import dev.horizon.basepoint.Direction;
import dev.horizon.cinder.Database;
import dev.horizon.cinder.DuplicateKeyException;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.declaration.IdentifierFormat;
import dev.horizon.pyrite.declaration.TypeSystem;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.storage.IdentifierBackend;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The identifiers of accounts, each under its account: a value of a declared identifier kind, such as an email
 * address, in the form that the kind normalizes. It adds them unverified, finds an account by one, and verifies them,
 * after which no other account verifies the value. A request that names no kind takes the first kind of the
 * declaration whose {@code match} the value meets.
 */
public final class IdentifierService {

  private static final String IDENTIFIERS = "identifiers/";

  private final IdentifierBackend identifiers;
  private final TypeSystem types;

  public IdentifierService(Database database, TypeSystem types) {
    this.identifiers = new IdentifierBackend(database);
    this.types = types;
  }

  /**
   * Adds an identifier to an account, unverified.
   *
   * @param account the name of the account
   * @param kind the declared kind, or empty for the kind that the value is of
   * @param value the value, which the kind normalizes
   * @param primary whether it is the primary identifier of its kind
   * @return the identifier, or a failure with {@code INVALID_ARGUMENT} for a kind that the declaration does not have
   * or a value of no kind, or with {@code ALREADY_EXISTS} if the account has the normalized value already
   */
  public Future<Tables.Identifier> add(String account, String kind, String value, boolean primary) {
    Optional<IdentifierFormat> format = kind(kind, value);
    if (format.isEmpty()) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, kind.isEmpty() ? "The value "
          + value + " is of no identifier kind of the declaration."
          : "The declaration has no identifier kind " + kind
              + "."));
    }
    IdentifierFormat declared = format.get();
    Optional<String> normalized = declared.normalize(value);
    if (normalized.isEmpty()) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The value " + value + " is not"
          + " of the identifier kind " + declared.name() + "."));
    }
    String normal = normalized.get();
    return identifiers.first(identifiers.query().where(and(eq("parent", account), eq("kind", declared.name()),
        eq("value", normal)))).compose(own -> {
          if (own.isPresent()) {
            return Future.failedFuture(new PyriteException(GrpcStatus.ALREADY_EXISTS, "The account " + account
                + " has the " + declared.name() + " " + normal + " already."));
          }
          String name = IDENTIFIERS + UUID.randomUUID();
          return identifiers.insert(Tables.Identifier.newBuilder().setName(name).setParent(account)
              .setKind(declared.name()).setValue(normal).setVerified(false).setPrimary(primary).setClaim(name)
              .build());
        });
  }

  /**
   * Reads the identifiers of an account.
   *
   * @param account the name of the account
   * @return the identifiers, verified or not, by name
   */
  public Future<List<Tables.Identifier>> identifiers(String account) {
    return identifiers.children(account);
  }

  /**
   * Finds the identifier of a name.
   *
   * @param name the name, such as {@code identifiers/0b0d...}
   * @return the identifier, or nothing if no identifier has the name
   */
  public Future<Optional<Tables.Identifier>> identifier(String name) {
    return identifiers.findByName(name);
  }

  /**
   * Finds the identifier of a value, as an exchange that starts from one does: the verified one, or else the one that
   * an account added first, so that an account that adds the value of another later never takes its sign-ins. A value
   * of no kind is no failure when the request names no kind, so that a sign-in tells nothing of what Pyrite accepts.
   *
   * @param kind the declared kind, or empty for the kind that the value is of
   * @param value the value, as the person typed it
   * @return the identifier, or nothing if no account has the value, or it is of no kind; or a failure with
   * {@code INVALID_ARGUMENT} for a kind that the declaration does not have, or a value that is not of the kind named
   */
  public Future<Optional<Tables.Identifier>> find(String kind, String value) {
    return normalForm(kind, value).compose(form -> form.isEmpty() ? Future.succeededFuture(Optional.empty())
        : identifiers.first(identifiers.query().where(and(eq("kind", form.get().kind()),
            eq("value", form.get().value()))).orderBy("verified", Direction.DESC).orderBy("create_time")));
  }

  /**
   * Finds the identifier of a value that an account holds, verified or not.
   *
   * @param account the name of the account
   * @param kind the declared kind, or empty for the kind that the value is of
   * @param value the value, as the person typed it
   * @return the identifier, or nothing if the account does not hold the value; or a failure with
   * {@code INVALID_ARGUMENT} as {@link #find(String, String)} gives it
   */
  public Future<Optional<Tables.Identifier>> find(String account, String kind, String value) {
    return normalForm(kind, value).compose(form -> form.isEmpty() ? Future.succeededFuture(Optional.empty())
        : identifiers.first(identifiers.query().where(and(eq("parent", account), eq("kind", form.get().kind()),
            eq("value", form.get().value())))));
  }

  /**
   * Marks an identifier as verified, and claims its value, so that no other account verifies it.
   *
   * @param identifier the identifier, as it was read
   * @return the verified identifier, or a failure with {@code ALREADY_EXISTS} when another account verified the value,
   * or with {@code ABORTED} when another change came first
   */
  public Future<Tables.Identifier> verify(Tables.Identifier identifier) {
    return identifiers.update(identifier.toBuilder().setVerified(true).setClaim(identifier.getKind() + ":"
        + identifier.getValue()).build(), identifier.getUid(), identifier.getRevision(), identifier.getName())
        .recover(failure -> Future.failedFuture(failure instanceof DuplicateKeyException
            ? new PyriteException(GrpcStatus.ALREADY_EXISTS, "The " + identifier.getKind() + " "
                + identifier.getValue() + " is verified by another account.")
            : failure));
  }

  /**
   * Tells whether a code can go to an identifier: its kind receives codes.
   */
  public boolean isDeliverable(Tables.Identifier identifier) {
    return types.identifierKind(identifier.getKind()).map(IdentifierFormat::receivesCodes).orElse(false);
  }

  /**
   * Gives an identifier as the authentication service gives it out.
   *
   * @return the message, with the name, the account, the kind, the value, and whether it is verified and primary
   */
  public static dev.horizon.pyrite.authentication.v1.Identifier message(Tables.Identifier row) {
    return dev.horizon.pyrite.authentication.v1.Identifier.newBuilder().setName(row.getName())
        .setAccount(row.getParent()).setKind(row.getKind()).setValue(row.getValue()).setVerified(row.getVerified())
        .setPrimary(row.getPrimary()).build();
  }

  /**
   * The kind of a value, and the value as the kind normalizes it.
   */
  private record NormalForm(String kind, String value) {
  }

  private Future<Optional<NormalForm>> normalForm(String kind, String value) {
    Optional<IdentifierFormat> declared = kind(kind, value);
    if (!kind.isEmpty() && declared.isEmpty()) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The declaration has no"
          + " identifier kind " + kind + "."));
    }
    Optional<String> normal = declared.flatMap(found -> found.normalize(value));
    if (!kind.isEmpty() && normal.isEmpty()) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The value " + value + " is not"
          + " of the identifier kind " + kind + "."));
    }
    return Future.succeededFuture(normal.map(found -> new NormalForm(declared.get().name(), found)));
  }

  private Optional<IdentifierFormat> kind(String kind, String value) {
    return kind.isEmpty() ? types.identifierKindOf(value) : types.identifierKind(kind);
  }
}
