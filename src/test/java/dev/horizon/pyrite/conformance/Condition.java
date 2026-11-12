package dev.horizon.pyrite.conformance;

/**
 * One step of a module: an action on Pyrite, or a check of what Pyrite did. It reads what the steps before it left in
 * the environment, and leaves what it produces there for the steps after it.
 */
@FunctionalInterface
public interface Condition {

  void evaluate(Environment environment) throws Exception;
}
