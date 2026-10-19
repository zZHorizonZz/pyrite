package dev.horizon.pyrite.internal;

import dev.horizon.pyrite.Encryption;
import dev.horizon.pyrite.Mac;
import dev.horizon.pyrite.Origin;
import dev.horizon.pyrite.Reason;
import dev.horizon.pyrite.Rotation;
import dev.horizon.pyrite.Signing;
import dev.horizon.pyrite.State;
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
import java.util.function.Function;
import java.util.function.Supplier;

final class Lifecycle {

  private static final int ATTEMPTS = 8;

  private final Provider provider;
  private final String name;
  private final Clock clock;
  private final Certificates certificates;

  Lifecycle(Provider provider, String name, Clock clock, Certificates certificates) {
    this.provider = provider;
    this.name = name;
    this.clock = clock;
    this.certificates = certificates;
  }

  String name() {
    return name;
  }

  Future<Settled> read() {
    Instant now = clock.instant();
    return provider.key(name).compose(key -> key.versions().map(versions -> new Settled(key, versions, now)));
  }

  Future<Settled> settled() {
    return settled(ATTEMPTS);
  }

  Future<VersionHandle> rotated() {
    return retried(() -> read().compose(state -> switch (state.key().purpose()) {
      case Signing signing -> state.pending()
          .map(Future::succeededFuture)
          .orElseGet(() -> state.key().generate(new State.Pending(state.now().plus(signing.ahead())),
              state.key().revision()));
      case Encryption encryption -> state.key().generate(new State.Primary(state.now()), state.key().revision());
      case Mac mac -> state.key().generate(new State.Primary(state.now()), state.key().revision());
    }), ATTEMPTS).compose(made -> settled().map(after -> after.version(made.id()).orElse(made)));
  }

  Future<VersionHandle> disabled(String id) {
    return changed(id, (state, version) -> switch (version.state()) {
      case State.Primary primary -> throw primary(id);
      case State.Disabled disabled -> throw stateless(id, "disabled");
      case State.Destroyed destroyed -> throw stateless(id, "destroyed");
      case State.Pending pending -> new State.Disabled(state.now());
      case State.Live live -> new State.Disabled(state.now());
    });
  }

  Future<VersionHandle> enabled(String id) {
    return changed(id, (state, version) -> switch (version.state()) {
      case State.Disabled disabled -> new State.Live(Instant.MAX);
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

  Future<VersionHandle> certified(VersionHandle version, Scheme scheme,
      Function<byte[], Future<byte[]>> signer) {
    return version.publicKey()
        .compose(key -> certificates.issued(name, key, scheme, version.created(), signer))
        .compose(certificate -> retried(() -> provider.key(name)
            .compose(key -> version.certificate(certificate, key.revision())), ATTEMPTS));
  }

  private Future<Settled> settled(int left) {
    return read()
        .compose(state -> step(state)
            .compose(changed -> changed && left > 0 ? settled(left - 1) : Future.succeededFuture(state)))
        .recover(failure -> failure instanceof AbortedException && left > 0
            ? settled(left - 1)
            : Future.failedFuture(failure));
  }

  private Future<Boolean> step(Settled state) {
    return switch (state.key().purpose()) {
      case Signing signing -> signing(state, signing);
      case Encryption encryption -> symmetric(state);
      case Mac mac -> symmetric(state);
    };
  }

  private Future<Boolean> symmetric(Settled state) {
    KeyHandle key = state.key();
    Revision revision = key.revision();
    List<VersionHandle> primaries = state.primaries();
    if (primaries.isEmpty()) {
      return key.generate(new State.Primary(state.now()), revision).map(true);
    }
    if (primaries.size() > 1) {
      return primaries.getLast().state(new State.Live(Instant.MAX), revision).map(true);
    }
    if (key.rotation() instanceof Rotation.Every every
        && !state.now().isBefore(since(primaries.getFirst()).plus(every.period()))) {
      return key.generate(new State.Primary(state.now()), revision).map(true);
    }
    return Future.succeededFuture(false);
  }

  private Future<Boolean> signing(Settled state, Signing signing) {
    KeyHandle key = state.key();
    Revision revision = key.revision();
    Instant now = state.now();
    Optional<VersionHandle> pending = state.pending();
    if (pending.isPresent() && pending.get().state() instanceof State.Pending due && !due.primaryAt().isAfter(now)) {
      return pending.get().state(new State.Primary(due.primaryAt()), revision).map(true);
    }
    List<VersionHandle> primaries = state.primaries();
    if (primaries.isEmpty()) {
      return key.generate(new State.Primary(now), revision).map(true);
    }
    if (primaries.size() > 1) {
      Instant replaced = since(primaries.get(primaries.size() - 2));
      return primaries.getLast().state(new State.Live(replaced.plus(signing.kept())), revision).map(true);
    }
    for (VersionHandle version : state.versions()) {
      if (version.origin() == Origin.GENERATED && version.state() instanceof State.Live live
          && !live.publishedUntil().isAfter(now)) {
        return version.state(new State.Disabled(live.publishedUntil()), revision).map(true);
      }
    }
    Scheme scheme = Scheme.valueOf(signing.algorithm().name());
    for (VersionHandle version : state.versions()) {
      if (version.origin() == Origin.GENERATED && state.published(version) && version.certificate().isEmpty()) {
        return certified(version, scheme, version::sign).map(true);
      }
    }
    if (key.rotation() instanceof Rotation.Every every && pending.isEmpty()) {
      Instant due = since(primaries.getFirst()).plus(every.period());
      Instant earliest = now.plus(signing.ahead());
      if (!now.isBefore(due.minus(signing.ahead()))) {
        return key.generate(new State.Pending(due.isAfter(earliest) ? due : earliest), revision).map(true);
      }
    }
    return Future.succeededFuture(false);
  }

  private Future<VersionHandle> changed(String id, BiFunction<Settled, VersionHandle, State> next) {
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

  private Instant since(VersionHandle primary) {
    return ((State.Primary) primary.state()).since();
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
