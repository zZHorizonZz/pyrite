package dev.horizon.pyrite.identity;

import static dev.horizon.basepoint.filter.Filter.and;
import static dev.horizon.basepoint.filter.Filter.eq;
import static dev.horizon.basepoint.filter.Filter.isNull;

import dev.horizon.cinder.Database;
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
 * The identifiers of accounts: it adds them in the normal form of their declared kind, and finds an account by one.
 * A request that names no kind takes the first kind of the declaration whose {@code match} the value meets.
 */
public final class IdentifierType {

  /**
   * The first segment of the name of an identifier, such as {@code identifiers/0b0d...}.
   */
  public static final String IDENTIFIERS = "identifiers/";

  private final IdentifierBackend identifiers;
  private final TypeSystem types;

  public IdentifierType(Database database, TypeSystem types) {
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
   * or a value of no kind, or with {@code ALREADY_EXISTS} if an account has the normalized value already
   */
  public Future<Identifier> add(String account, String kind, String value, boolean primary) {
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
    return live(declared.name(), normal).compose(found -> {
      if (found.isPresent()) {
        return Future.failedFuture(new PyriteException(GrpcStatus.ALREADY_EXISTS, "The " + declared.name() + " "
            + normal + " is another account's or this one's already."));
      }
      return identifiers.insert(Tables.Identifier.newBuilder().setName(IDENTIFIERS + UUID.randomUUID())
          .setParent(account).setKind(declared.name()).setValue(normal).setVerified(false).setPrimary(primary)
          .build()).map(row -> new Identifier(this, row));
    });
  }

  /**
   * Reads the identifiers of an account.
   *
   * @param account the name of the account
   * @return the identifiers, by name
   */
  public Future<List<Identifier>> identifiers(String account) {
    return identifiers.children(account).map(rows -> rows.stream().map(row -> new Identifier(this, row)).toList());
  }

  /**
   * Finds the identifier of a name.
   *
   * @param name the name, such as {@code identifiers/0b0d...}
   * @return the identifier, or nothing if no identifier has the name
   */
  public Future<Optional<Identifier>> identifier(String name) {
    return identifiers.findByName(name).map(found -> found.map(row -> new Identifier(this, row)));
  }

  /**
   * Finds the identifier of a value, as a flow that starts from one does. A value of no kind is no failure when the
   * request names no kind, so that a sign-in tells nothing of what Pyrite accepts.
   *
   * @param kind the declared kind, or empty for the kind that the value is of
   * @param value the value, as the person typed it
   * @return the identifier, or nothing if no account has the value, or it is of no kind; or a failure with
   * {@code INVALID_ARGUMENT} for a kind that the declaration does not have, or a value that is not of the kind named
   */
  public Future<Optional<Identifier>> find(String kind, String value) {
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
    return normal.isEmpty() ? Future.succeededFuture(Optional.empty()) : live(declared.get().name(), normal.get());
  }

  boolean receivesCodes(String kind) {
    return types.identifierKind(kind).map(IdentifierFormat::receivesCodes).orElse(false);
  }

  Future<Identifier> save(Identifier identifier, Tables.Identifier.Builder changed) {
    return identifiers.update(changed.build(), identifier.row().getUid(), identifier.row().getRevision(),
        identifier.name()).map(row -> new Identifier(this, row));
  }

  private Optional<IdentifierFormat> kind(String kind, String value) {
    return kind.isEmpty() ? types.identifierKindOf(value) : types.identifierKind(kind);
  }

  private Future<Optional<Identifier>> live(String kind, String value) {
    return identifiers.first(identifiers.query().where(and(eq("kind", kind), eq("value", value), isNull(
        "delete_time")))).map(found -> found.map(row -> new Identifier(this, row)));
  }
}
