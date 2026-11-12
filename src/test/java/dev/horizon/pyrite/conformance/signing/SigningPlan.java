package dev.horizon.pyrite.conformance.signing;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Plan;
import java.util.List;
import java.util.function.Supplier;

/**
 * The plan of signing: JWTs, their claims, forgeries, and detached signatures.
 */
public final class SigningPlan implements Plan {

  @Override
  public String name() {
    return "signing";
  }

  @Override
  public List<Supplier<ConformanceModule>> modules() {
    return List.<Supplier<ConformanceModule>> of(PublicKeyPublication::new, MacKey::new, EncryptionKeySigning::new,
        TokenIssuer::new,
        TokenAudience::new, TokenExpiry::new, TokenNotBefore::new, TokenLeeway::new, TokenType::new, PayloadForgery::new,
        SignatureForgery::new, AlgorithmNone::new, AlgorithmConfusion::new, KeyIdBorrowing::new, TokenOfOtherKey::new,
        JwtSyntax::new, CriticalHeader::new, DetachedData::new, DetachedForgery::new, UnknownSigningVersion::new,
        SigningRelation::new, EmptySigningRequest::new);
  }
}
