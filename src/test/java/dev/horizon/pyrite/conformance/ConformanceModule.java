package dev.horizon.pyrite.conformance;

import java.util.ArrayList;
import java.util.List;

/**
 * One scenario of Pyrite's contract: the steps that it calls in order. A step that only prepares the scenario fails
 * the module as broken; a step that checks a requirement fails it with the requirement, and either stops it or lets
 * the steps after it run.
 */
public abstract class ConformanceModule {

  private final List<String> trace = new ArrayList<>();
  private final List<String> failures = new ArrayList<>();
  private Environment environment;

  /**
   * Calls the steps of the scenario.
   */
  protected abstract void run() throws Exception;

  /**
   * Tells whether the module runs in a variant; by default it runs in all of them.
   */
  public boolean appliesTo(Variant variant) {
    return true;
  }

  public String summary() {
    Summary summary = getClass().getAnnotation(Summary.class);
    if (summary == null) {
      throw new IllegalStateException(getClass().getSimpleName() + " has no @Summary");
    }
    return summary.value();
  }

  protected final Environment environment() {
    return environment;
  }

  /**
   * Calls a step that prepares the scenario.
   */
  protected final void call(Condition condition) {
    try {
      condition.evaluate(environment);
      trace.add("  ok    " + name(condition));
    } catch (Exception | AssertionError broken) {
      trace.add("  BROKE " + name(condition) + ": " + broken.getMessage());
      throw new Stop(broken);
    }
  }

  /**
   * Calls a step that checks a requirement, and stops at its failure.
   */
  protected final void callAndStopOnFailure(Condition condition, Requirement requirement) {
    if (!check(condition, requirement)) {
      throw new Stop(null);
    }
  }

  /**
   * Calls a step that checks a requirement, and goes on after its failure.
   */
  protected final void callAndContinueOnFailure(Condition condition, Requirement requirement) {
    check(condition, requirement);
  }

  final void run(Environment environment) throws Exception {
    this.environment = environment;
    try {
      run();
    } catch (Stop stop) {
      if (stop.getCause() != null) {
        throw new AssertionError(report("broke"), stop.getCause());
      }
    }
    if (!failures.isEmpty()) {
      throw new AssertionError(report("failed " + String.join(", ", failures)));
    }
  }

  private boolean check(Condition condition, Requirement requirement) {
    try {
      condition.evaluate(environment);
      trace.add("  ok    " + name(condition) + " (" + requirement.name() + ")");
      return true;
    } catch (Exception | AssertionError failure) {
      trace.add("  FAIL  " + name(condition) + " (" + requirement + "): " + failure.getMessage().strip());
      failures.add(requirement.name());
      return false;
    }
  }

  private String report(String outcome) {
    return summary() + " " + outcome + " in " + environment.variant() + "\n" + String.join("\n", trace);
  }

  private static String name(Condition condition) {
    String name = condition.toString();
    return name.contains("$$Lambda") ? condition.getClass().getSimpleName() : name;
  }

  /**
   * Ends a run at a step.
   */
  private static final class Stop extends RuntimeException {

    Stop(Throwable cause) {
      super(null, cause, false, false);
    }
  }
}
