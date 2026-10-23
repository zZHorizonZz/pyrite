package dev.horizon.pyrite.authentication.impl;

import static dev.horizon.basepoint.Filter.and;
import static dev.horizon.basepoint.Filter.eq;
import static dev.horizon.basepoint.Filter.isNull;

import dev.horizon.cinder.Database;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.authentication.v1.IdentifierKind;
import dev.horizon.pyrite.impl.model.DeclaredType;
import dev.horizon.pyrite.impl.resource.BaseResourceType;
import dev.horizon.pyrite.impl.resource.Resource;
import dev.horizon.pyrite.impl.resource.SchemaImpl;
import dev.horizon.pyrite.impl.storage.Row;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class IdentifierTypeImpl extends BaseResourceType<Identifier> implements IdentifierType {

  private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
  private static final Pattern PHONE = Pattern.compile("\\+[1-9][0-9]{6,14}");
  private static final Pattern USERNAME = Pattern.compile("[a-z0-9][a-z0-9._-]{2,63}");

  public IdentifierTypeImpl(DeclaredType declared, SchemaImpl schema, Database database) {
    super(declared, schema, database, Set.of(VERIFIED), Set.of(), Set.of(KIND,
        VALUE), true);
  }

  @Override
  protected Identifier resource(Row row) {
    return new IdentifierImpl(this, row);
  }

  @Override
  protected Future<Identifier> insert(Optional<Resource> parent, String id, Map<String, Object> values) {
    IdentifierKind kind = Arrays.stream(IdentifierKind.values())
        .filter(known -> known != IdentifierKind.IDENTIFIER_KIND_UNSPECIFIED && known != IdentifierKind.UNRECOGNIZED
            && known.name().equals(values.get(KIND)))
        .findFirst().orElseThrow(() -> new PyriteException(GrpcStatus.INVALID_ARGUMENT, "An identifier's kind is"
            + " EMAIL, PHONE or USERNAME."));
    if (!(values.get(VALUE) instanceof String value)) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "An identifier has a value.");
    }
    Map<String, Object> own = new HashMap<>(values);
    own.put(VALUE, normalize(kind, value));
    own.put(VERIFIED, false);
    own.putIfAbsent(PRIMARY, false);
    return super.insert(parent, id, own);
  }

  @Override
  public Future<Optional<Identifier>> find(IdentifierKind kind, String value) {
    return first(and(eq(KIND, kind.name()), eq(VALUE, normalize(kind, value)),
        isNull(Resource.DELETE_TIME)));
  }

  private static String normalize(IdentifierKind kind, String value) {
    String normalized = switch (kind) {
      case EMAIL, USERNAME -> value.strip().toLowerCase(Locale.ROOT);
      case PHONE -> value.strip().replace(" ", "").replace("-", "");
      case IDENTIFIER_KIND_UNSPECIFIED, UNRECOGNIZED -> throw new PyriteException(GrpcStatus.INVALID_ARGUMENT,
          "The identifier names no kind: EMAIL, PHONE or USERNAME.");
    };
    Pattern pattern = switch (kind) {
      case EMAIL -> EMAIL;
      case PHONE -> PHONE;
      default -> USERNAME;
    };
    if (!pattern.matcher(normalized).matches()) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The " + kind.name().toLowerCase(Locale.ROOT) + " "
          + value + " does not match " + pattern.pattern() + ".");
    }
    return normalized;
  }
}
