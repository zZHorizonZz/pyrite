package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sets handlers of sessions that keep the names of the sessions issued and ended.
 */
public record ObserveSessions() implements Condition {

  @Override
  public void evaluate(Environment environment) {
    List<String> issues = new CopyOnWriteArrayList<>();
    List<String> ends = new CopyOnWriteArrayList<>();
    environment.put(Value.SESSION_ISSUES, issues);
    environment.put(Value.SESSION_ENDS, ends);
    environment.pyrite().service.sessionIssueHandler(session -> issues.add(session.getName()))
        .sessionEndHandler(session -> ends.add(session.getName()));
  }
}
