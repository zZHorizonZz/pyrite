package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;

/**
 * Sets a handler of issued sessions that throws.
 */
public record ThrowOnSessionIssue() implements Condition {

  @Override
  public void evaluate(Environment environment) {
    environment.pyrite().service.sessionIssueHandler(session -> {
      throw new IllegalStateException("The audit log is full.");
    });
  }
}
