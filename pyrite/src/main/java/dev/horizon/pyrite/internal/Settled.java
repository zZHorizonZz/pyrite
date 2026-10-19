package dev.horizon.pyrite.internal;

import dev.horizon.pyrite.State;
import dev.horizon.pyrite.spi.KeyHandle;
import dev.horizon.pyrite.spi.VersionHandle;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

record Settled(KeyHandle key, List<VersionHandle> versions, Instant now) {

  Settled {
    versions = List.copyOf(versions);
  }

  List<VersionHandle> primaries() {
    return versions.stream().filter(version -> version.state() instanceof State.Primary).toList();
  }

  Optional<VersionHandle> primary() {
    return primaries().stream().findFirst();
  }

  Optional<VersionHandle> pending() {
    return versions.stream().filter(version -> version.state() instanceof State.Pending).findFirst();
  }

  Optional<VersionHandle> version(String id) {
    return versions.stream().filter(version -> version.id().equals(id)).findFirst();
  }

  boolean published(VersionHandle version) {
    return switch (version.state()) {
      case State.Pending pending -> true;
      case State.Primary primary -> true;
      case State.Live live -> live.publishedUntil().isAfter(now);
      case State.Disabled disabled -> false;
      case State.Destroyed destroyed -> false;
    };
  }

  List<VersionHandle> live() {
    List<VersionHandle> live = new ArrayList<>();
    primary().ifPresent(live::add);
    for (VersionHandle version : versions) {
      if (!(version.state() instanceof State.Primary) && published(version)) {
        live.add(version);
      }
    }
    return live;
  }
}
