package dev.horizon.pyrite.internal;

import dev.horizon.pyrite.Reason;
import dev.horizon.pyrite.Rotation;
import dev.horizon.pyrite.State;
import dev.horizon.pyrite.Validity;
import dev.horizon.pyrite.common.AbortedException;
import dev.horizon.pyrite.common.FailedPreconditionException;
import dev.horizon.pyrite.common.NotFoundException;
import dev.horizon.pyrite.common.Revision;
import dev.horizon.pyrite.spi.KeyHandle;
import dev.horizon.pyrite.spi.Provider;
import dev.horizon.pyrite.spi.VersionHandle;
import io.vertx.core.Future;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

final class Lifecycle {

  private static final int ATTEMPTS = 8;
  private static final int STEPS = 64;

  private final Provider provider;
  private final String name;
  private final Clock clock;

  Lifecycle(Provider provider, String name, Clock clock) {
    this.provider = provider;
    this.name = name;
    this.clock = clock;
  }

  String name() {
    return name;
  }

  Future<Snapshot> read() {
    Instant now = clock.instant();
    return provider.key(name).compose(key -> key.versions().map(versions -> new Snapshot(key, versions, now)));
  }

  Future<Snapshot> settled() {
    return settled(ATTEMPTS, STEPS);
  }

  Future<VersionHandle> rotated() {
    return retried(() -> read().compose(state -> state.pending()
        .map(Future::succeededFuture)
        .orElseGet(() -> made(state, state.now()))), ATTEMPTS)
        .compose(made -> settled().map(after -> after.version(made.id()).orElse(made)));
  }

  Future<VersionHandle> disabled(String id) {
    return changed(id, (state, version) -> switch (version.state()) {
      case State.Primary primary -> throw primary(id);
      case State.Disabled disabled -> throw stateless(id, "disabled");
      case State.Expired expired -> throw stateless(id, "expired");
      case State.Destroyed destroyed -> throw stateless(id, "destroyed");
      case State.Pending pending -> new State.Disabled(state.now());
      case State.Live live -> new State.Disabled(state.now());
    });
  }

  Future<VersionHandle> enabled(String id) {
    return changed(id, (state, version) -> switch (version.state()) {
      case State.Disabled disabled when version.expires().isAfter(state.now()) -> new State.Live();
      case State.Disabled disabled -> throw stateless(id, "past its lifetime");
      default -> throw stateless(id, "not disabled");
    });
  }

  Future<VersionHandle> destroyed(String id) {
    return changed(id, (state, version) -> switch (version.state()) {
      case State.Primary primary -> throw primary(id);
      case State.Destroyed destroyed -> throw stateless(id, "destroyed");
      default -> new State.Destroyed(state.now());
    });
  }

  Instant expiry(Validity validity, Instant made) {
    return switch (validity) {
      case Validity.Limited limited -> made.plus(limited.lifetime());
      case Validity.Unlimited unlimited -> Instant.MAX;
    };
  }

  private Future<Snapshot> settled(int attempts, int steps) {
    return read()
        .compose(state -> step(state)
            .compose(changed -> changed && steps > 0
                ? settled(attempts, steps - 1)
                : Future.succeededFuture(state)))
        .recover(failure -> failure instanceof AbortedException && attempts > 0
            ? settled(attempts - 1, steps)
            : Future.failedFuture(failure));
  }

  private Future<Boolean> step(Snapshot state) {
    KeyHandle key = state.key();
    Revision revision = key.revision();
    Instant now = state.now();
    for (VersionHandle version : state.versions()) {
      boolean expiring = switch (version.state()) {
        case State.Expired expired -> false;
        case State.Destroyed destroyed -> false;
        default -> !version.expires().isAfter(now);
      };
      if (expiring) {
        return version.state(new State.Expired(version.expires()), revision).map(true);
      }
    }
    Optional<VersionHandle> pending = state.pending();
    if (pending.isPresent() && pending.get().state() instanceof State.Pending due && !due.primaryAt().isAfter(now)) {
      return pending.get().state(new State.Primary(due.primaryAt()), revision).map(true);
    }
    List<VersionHandle> primaries = state.primaries();
    if (primaries.size() > 1) {
      return primaries.getLast().state(new State.Live(), revision).map(true);
    }
    if (key.rotation() instanceof Rotation.Every every && pending.isEmpty()) {
      if (primaries.isEmpty()) {
        return key.generate(new State.Primary(now), expiry(key.validity(), now), revision).map(true);
      }
      Instant due = ((State.Primary) primaries.getFirst().state()).since().plus(every.period());
      if (!now.isBefore(due.minus(key.validity().pending()))) {
        return made(state, due).map(true);
      }
    }
    return Future.succeededFuture(false);
  }

  private Future<VersionHandle> made(Snapshot state, Instant due) {
    KeyHandle key = state.key();
    Instant earliest = state.now().plus(key.validity().pending());
    Instant primaryAt = due.isAfter(earliest) ? due : earliest;
    State first = primaryAt.isAfter(state.now()) ? new State.Pending(primaryAt) : new State.Primary(state.now());
    return key.generate(first, expiry(key.validity(), state.now()), key.revision());
  }

  private Future<VersionHandle> changed(String id, BiFunction<Snapshot, VersionHandle, State> next) {
    return retried(() -> read().compose(state -> {
      VersionHandle version = state.version(id).orElseThrow(() -> missing(id));
      return version.state(next.apply(state, version), state.key().revision());
    }), ATTEMPTS);
  }

  private <T> Future<T> retried(Supplier<Future<T>> attempt, int left) {
    return attempt.get().recover(failure -> failure instanceof AbortedException && left > 0
        ? retried(attempt, left - 1)
        : Future.failedFuture(failure));
  }

  private NotFoundException missing(String id) {
    return new NotFoundException("version", name + "/versions/" + id);
  }

  private FailedPreconditionException primary(String id) {
    return new FailedPreconditionException(Reason.PRIMARY.name(),
        "The version " + id + " of " + name + " is primary; rotate the key first");
  }

  private FailedPreconditionException stateless(String id, String state) {
    return new FailedPreconditionException(Reason.VERSION.name(), "The version " + id + " of " + name + " is " + state);
  }
}
