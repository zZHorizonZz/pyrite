package dev.horizon.pyrite.conformance.encryption;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Plan;
import java.util.List;
import java.util.function.Supplier;

/**
 * The plan of encryption: round trips, tampering, permissions, and keys.
 */
public final class EncryptionPlan implements Plan {

  @Override
  public String name() {
    return "encryption";
  }

  @Override
  public List<Supplier<ConformanceModule>> modules() {
    return List.<Supplier<ConformanceModule>> of(EncryptionRoundTrip::new, PlaintextSize::new, RandomEncryption::new,
        ByteFlip::new, CiphertextTruncation::new, CiphertextExtension::new, VersionRename::new, AssociatedData::new,
        CiphertextOfOtherKey::new, ForeignByteRefusal::new, NoKeyRelation::new, KeyRelationSeparation::new,
        ViewerDecryption::new,
        DecrypterRevocation::new, EncryptionPurpose::new, MissingKey::new, NameOfOtherType::new, KeyAlgorithm::new,
        NegativePeriod::new, KeyCreatePermission::new);
  }
}
