package dev.horizon.pyrite.identity;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.RecoveryCodes;
import java.util.List;

/**
 * A new set of recovery codes: the credential that holds their MACs, and the codes, which Pyrite gives out only once,
 * in the result of the flow that creates them.
 *
 * @param credential the credential that holds their MACs
 * @param codes the codes
 */
public record RecoveryCodeSet(RecoveryCodeCredential credential, List<String> codes) {

  public RecoveryCodeSet {
    codes = List.copyOf(codes);
  }

  /**
   * Gives the codes as the result of a flow gives them out, in {@code issued}.
   *
   * @return the codes, as {@code RecoveryCodes} packed in an {@code Any}
   */
  public Any toAny() {
    return Any.pack(RecoveryCodes.newBuilder().addAllCodes(codes).build());
  }
}
