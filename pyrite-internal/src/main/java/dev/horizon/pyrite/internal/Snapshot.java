package dev.horizon.pyrite.internal;

import dev.horizon.pyrite.Reason;
import dev.horizon.pyrite.State;
import dev.horizon.pyrite.common.FailedPreconditionException;
import dev.horizon.pyrite.spi.KeyHandle;
import dev.horizon.pyrite.spi.VersionHandle;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

record Snapshot(KeyHandle key, List<VersionHandle> versions, Instant now) {

  Snapshot {
    versions = List.copyOf(versions);
  }

  List<VersionHandle> primaries() {
    return versions.stream().filter(version -> version.state() instanceof State.Primary).toList();
  }

  Optional<VersionHandle> primary() {
    return primaries().stream().findFirst();
  }

  VersionHandle current() {
    return primary().orElseThrow(() -> new FailedPreconditionException(Reason.PRIMARY.name(), "The key " + key.name()
        + " has no primary version, because the last one expired and the key does not rotate; rotate it"));
  }

  Optional<VersionHandle> pending() {
    return versions.stream().filter(version -> version.state() instanceof State.Pending).findFirst();
  }

  Optional<VersionHandle> version(String id) {
    return versions.stream().filter(version -> version.id().equals(id)).findFirst();
  }

  List<VersionHandle> live() {
    List<VersionHandle> live = new ArrayList<>();
    primary().ifPresent(live::add);
    for (VersionHandle version : versions) {
      if (!(version.state() instanceof State.Primary) && version.state().live()) {
        live.add(version);
      }
    }
    return live;
  }
}
