package dev.horizon.pyrite.identity;

import dev.horizon.pyrite.authentication.v1.ProviderAccount;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import java.util.Set;

/**
 * A linked account at a provider, such as Google or GitHub, whose sign-in counts as a factor of its own. An account has
 * as many as it links, and no account at a provider is linked to two accounts. The credential keeps the provider and
 * the id of the account at it, as {@code google:1093...}.
 */
public final class ProviderCredential extends Credential<ProviderAccount> {

  ProviderCredential(CredentialType type, Tables.Factor row) {
    super(type, row);
  }

  /**
   * Gives the name of the provider.
   *
   * @return the name, such as {@code google}
   */
  public String provider() {
    return row().getCredentialId().substring(0, row().getCredentialId().indexOf(':'));
  }

  /**
   * Gives the id of the account at the provider.
   *
   * @return the id, such as the {@code sub} of an ID token
   */
  public String accountId() {
    return row().getCredentialId().substring(row().getCredentialId().indexOf(':') + 1);
  }

  /**
   * Verifies an account at a provider, which a sign-in at the provider proved.
   *
   * @return {@link Proof#FEDERATION} when it is the linked account
   */
  @Override
  public Future<Set<Proof>> verify(ProviderAccount answer, byte[] nonce) {
    return Future.succeededFuture(answer.getProvider().equals(provider()) && answer.getId().equals(accountId())
        ? Set.of(Proof.FEDERATION)
        : Set.of());
  }

  @Override
  boolean replacesActive() {
    return false;
  }

  @Override
  boolean signsInAlone() {
    return true;
  }
}
