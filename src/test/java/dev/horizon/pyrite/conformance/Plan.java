package dev.horizon.pyrite.conformance;

import java.util.List;
import java.util.function.Supplier;

/**
 * A group of modules, which run in every variant.
 */
public interface Plan {

  String name();

  List<Supplier<ConformanceModule>> modules();
}
