package dev.horizon.pyrite.memory;

import dev.horizon.pyrite.Origin;
import dev.horizon.pyrite.Purpose;
import dev.horizon.pyrite.Reason;
import dev.horizon.pyrite.Rotation;
import dev.horizon.pyrite.State;
import dev.horizon.pyrite.common.AbortedException;
import dev.horizon.pyrite.common.FailedPreconditionException;
import dev.horizon.pyrite.common.NotFoundException;
import dev.horizon.pyrite.common.Revision;
import dev.horizon.pyrite.internal.Generator;
import dev.horizon.pyrite.internal.Material;
import dev.horizon.pyrite.spi.VersionHandle;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class MemoryEntry {

  private final Generator generator;
  private final Clock clock;
  private final String name;
  private final Purpose<?> purpose;
  private final Instant created;
  private final List<Slot> slots = new ArrayList<>();
  private Rotation rotation;
  private long revision;
  private long numbered;

  MemoryEntry(Generator generator, Clock clock, String name, Purpose<?> purpose, Rotation rotation, Material first,
      State state) {
    this.generator = generator;
    this.clock = clock;
    this.name = name;
    this.purpose = purpose;
    this.rotation = rotation;
    this.created = clock.instant();
    this.numbered = 1;
    this.slots.add(new Slot(String.valueOf(numbered), Origin.GENERATED, created, state, first));
    this.revision = 1;
  }

  Generator generator() {
    return generator;
  }

  synchronized MemoryKey key() {
    return new MemoryKey(this, name, purpose, rotation, new Revision(revision), created);
  }

  synchronized List<VersionHandle> versions() {
    return slots.stream().map(this::version).map(VersionHandle.class::cast).toList();
  }

  synchronized MemoryKey rotation(Rotation rotation, Revision expected) {
    current(expected);
    this.rotation = rotation;
    revision++;
    return key();
  }

  synchronized MemoryVersion added(Material material, Origin origin, State state, Revision expected) {
    current(expected);
    numbered++;
    Slot slot = new Slot(String.valueOf(numbered), origin, clock.instant(), state, material);
    slots.addFirst(slot);
    revision++;
    return version(slot);
  }

  synchronized MemoryVersion state(String id, State state, Revision expected) {
    current(expected);
    Slot slot = slot(id);
    slot.state = state;
    if (state instanceof State.Destroyed) {
      slot.material = null;
    }
    revision++;
    return version(slot);
  }

  synchronized MemoryVersion certificate(String id, byte[] certificate, Revision expected) {
    current(expected);
    Slot slot = slot(id);
    slot.certificate = certificate.clone();
    revision++;
    return version(slot);
  }

  synchronized Material material(String id) {
    Slot slot = slot(id);
    if (slot.material == null) {
      throw new FailedPreconditionException(Reason.VERSION.name(),
          "The version " + id + " of " + name + " is destroyed");
    }
    return slot.material;
  }

  private MemoryVersion version(Slot slot) {
    return new MemoryVersion(this, slot.id, slot.state, slot.origin, slot.created,
        slot.certificate == null ? null : slot.certificate.clone());
  }

  private Slot slot(String id) {
    return slots.stream()
        .filter(slot -> slot.id.equals(id))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("version", name + "/versions/" + id));
  }

  private void current(Revision expected) {
    if (revision != expected.value()) {
      throw new AbortedException("key", name, expected);
    }
  }

  private static final class Slot {

    private final String id;
    private final Origin origin;
    private final Instant created;
    private State state;
    private Material material;
    private byte[] certificate;

    private Slot(String id, Origin origin, Instant created, State state, Material material) {
      this.id = id;
      this.origin = origin;
      this.created = created;
      this.state = state;
      this.material = material;
    }
  }
}
