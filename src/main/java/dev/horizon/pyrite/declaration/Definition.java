package dev.horizon.pyrite.declaration;

import dev.horizon.spectrum.openfga.v1.RelationReference;
import dev.horizon.spectrum.openfga.v1.Userset;
import java.util.List;

/**
 * A relation of a type of a declaration, as the authorization model takes it: its rewrite, and the types that it
 * takes directly.
 *
 * @param rewrite the rewrite, such as a union of {@code this} and a {@code tuple_to_userset} through {@code parent}
 * @param direct the types that take the relation directly, from the type's {@code metadata}; empty for a relation
 * without {@code this}
 */
public record Definition(Userset rewrite, List<RelationReference> direct) {

  public Definition {
    direct = List.copyOf(direct);
  }
}
