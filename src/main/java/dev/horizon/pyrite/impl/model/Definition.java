package dev.horizon.pyrite.impl.model;

import dev.horizon.spectrum.openfga.v1.RelationReference;
import dev.horizon.spectrum.openfga.v1.Userset;
import java.util.List;

public record Definition(Userset rewrite, List<RelationReference> direct) {

  public Definition {
    direct = List.copyOf(direct);
  }
}
