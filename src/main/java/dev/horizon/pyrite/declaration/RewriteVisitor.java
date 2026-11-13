package dev.horizon.pyrite.declaration;

import dev.horizon.spectrum.openfga.v1.Userset;
import java.util.List;

/**
 * A walk over the rewrite of a relation: one method for each kind of userset, which gets what the walk gave for the
 * usersets in it.
 *
 * @param <R> what the walk gives
 */
interface RewriteVisitor<R> {

  /**
   * Walks a rewrite, from the usersets in it up.
   *
   * @param userset the rewrite
   * @param visitor the visitor of each userset
   * @param <R> what the walk gives
   * @return what the visitor gives for the rewrite
   */
  static <R> R walk(Userset userset, RewriteVisitor<R> visitor) {
    return switch (userset.getUsersetCase()) {
      case THIS -> visitor.direct();
      case COMPUTED_USERSET -> visitor.computed(userset.getComputedUserset().getRelation());
      case TUPLE_TO_USERSET -> visitor.tupleToUserset(userset.getTupleToUserset().getTupleset().getRelation(),
          userset.getTupleToUserset().getComputedUserset().getRelation());
      case UNION -> visitor.union(userset.getUnion().getChildList().stream().map(child -> walk(child, visitor))
          .toList());
      case INTERSECTION -> visitor.intersection(userset.getIntersection().getChildList().stream()
          .map(child -> walk(child, visitor)).toList());
      case DIFFERENCE -> visitor.difference(walk(userset.getDifference().getBase(), visitor),
          walk(userset.getDifference().getSubtract(), visitor));
      case USERSET_NOT_SET -> visitor.empty();
    };
  }

  /**
   * Visits {@code this}: the subjects that a tuple grants the relation to.
   */
  R direct();

  /**
   * Visits the subjects of another relation of the same object.
   */
  R computed(String relation);

  /**
   * Visits the subjects of a relation of the objects that a relation of this object names, such as {@code admin} from
   * {@code parent}.
   */
  R tupleToUserset(String tupleset, String relation);

  /**
   * Visits the subjects of any of the children.
   */
  R union(List<R> children);

  /**
   * Visits the subjects of every one of the children.
   */
  R intersection(List<R> children);

  /**
   * Visits the subjects of the base that are not subjects of the subtract.
   */
  R difference(R base, R subtract);

  /**
   * Visits a rewrite that names nothing.
   */
  R empty();
}
