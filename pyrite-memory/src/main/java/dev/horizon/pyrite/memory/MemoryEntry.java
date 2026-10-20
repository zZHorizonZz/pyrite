package dev.horizon.pyrite.memory;

import dev.horizon.pyrite.Origin;
import dev.horizon.pyrite.Purpose;
import dev.horizon.pyrite.Reason;
import dev.horizon.pyrite.Rotation;
import dev.horizon.pyrite.State;
import dev.horizon.pyrite.Validity;
import dev.horizon.pyrite.common.AbortedException;
import dev.horizon.pyrite.common.FailedPreconditionException;
import dev.horizon.pyrite.common.NotFoundException;
import dev.horizon.pyrite.common.Revision;
import dev.horizon.pyrite.internal.Generator;
import dev.horizon.pyrite.internal.Material;
import dev.horizon.pyrite.internal.Vault;
import dev.horizon.pyrite.internal.VersionName;
import dev.horizon.pyrite.spi.VersionHandle;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class MemoryEntry {

  private static final byte[] NONE = new byte[0];

  private final Generator generator;
  private final Vault vault;
  private final Clock clock;
  private final String name;
  private final Purpose<?> purpose;
  private final Instant created;
  private final List<Slot> slots = new ArrayList<>();
  private Rotation rotation;
  private Validity validity;
  private long revision;

  MemoryEntry(Generator generator, Vault vault, Clock clock, String name, Purpose<?> purpose, Rotation rotation,
      Validity validity, Material first, State state, Instant expires) {
    this.generator = generator;
    this.vault = vault;
    this.clock = clock;
    this.name = name;
    this.purpose = purpose;
    this.rotation = rotation;
    this.validity = validity;
    this.created = clock.instant();
    this.slots.add(slot(1, Origin.GENERATED, created, state, expires, first));
    this.revision = 1;
  }

  Generator generator() {
    return generator;
  }

  synchronized MemoryKey key() {
    return new MemoryKey(this, name, purpose, rotation, validity, new Revision(revision), created);
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

  synchronized MemoryKey validity(Validity validity, Revision expected) {
    current(expected);
    this.validity = validity;
    revision++;
    return key();
  }

  synchronized MemoryVersion added(Material material, Origin origin, State state, Instant expires, Revision expected) {
    current(expected);
    Slot slot = slot(slots.getFirst().name.number() + 1, origin, clock.instant(), state, expires, material);
    slots.addFirst(slot);
    revision++;
    return version(slot);
  }

  synchronized MemoryVersion state(String id, State state, Revision expected) {
    current(expected);
    Slot slot = slot(id);
    slot.state = state;
    if (state instanceof State.Destroyed) {
      slot.sealed = null;
    }
    vault.forget(slot.name);
    revision++;
    return version(slot);
  }

  synchronized Material material(String id) {
    Slot slot = slot(id);
    if (slot.sealed == null) {
      throw new FailedPreconditionException(Reason.VERSION.name(), "The version " + slot.name + " is destroyed");
    }
    return vault.unseal(slot.name, purpose, slot.sealed, slot.subjectPublicKeyInfo);
  }

  private Slot slot(long number, Origin origin, Instant created, State state, Instant expires, Material material) {
    VersionName version = new VersionName(name, number);
    return new Slot(version, origin, created, expires, state, vault.seal(version, material).orElse(NONE),
        material.subjectPublicKeyInfo().orElse(NONE));
  }

  private MemoryVersion version(Slot slot) {
    return new MemoryVersion(this, String.valueOf(slot.name.number()), slot.state, slot.origin, slot.created,
        slot.expires);
  }

  private Slot slot(String id) {
    return slots.stream()
        .filter(slot -> String.valueOf(slot.name.number()).equals(id))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("version", name + "/versions/" + id));
  }

  private void current(Revision expected) {
    if (revision != expected.value()) {
      throw new AbortedException("key", name, expected);
    }
  }

  private static final class Slot {

    private final VersionName name;
    private final Origin origin;
    private final Instant created;
    private final Instant expires;
    private final byte[] subjectPublicKeyInfo;
    private State state;
    private byte[] sealed;

    private Slot(VersionName name, Origin origin, Instant created, Instant expires, State state, byte[] sealed,
        byte[] subjectPublicKeyInfo) {
      this.name = name;
      this.origin = origin;
      this.created = created;
      this.expires = expires;
      this.state = state;
      this.sealed = sealed;
      this.subjectPublicKeyInfo = subjectPublicKeyInfo;
    }
  }
}
