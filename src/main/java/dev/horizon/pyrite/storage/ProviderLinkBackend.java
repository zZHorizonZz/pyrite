package dev.horizon.pyrite.storage;

import static dev.horizon.basepoint.filter.Filter.and;
import static dev.horizon.basepoint.filter.Filter.eq;

import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import java.util.Optional;

/**
 * The rows that link accounts at providers to the factors of accounts. The provider and the id of an account at it are
 * unique together, so an insert of an account at a provider that is linked already fails with a
 * {@link dev.horizon.cinder.DuplicateKeyException}.
 *
 * @param database the database of Pyrite's tables
 */
public record ProviderLinkBackend(Database database) implements Backend<Tables.ProviderLink, String> {

  @Override
  public Tables.ProviderLink prototype() {
    return Tables.ProviderLink.getDefaultInstance();
  }

  /**
   * Finds the link of an account at a provider.
   *
   * @param provider the name of the provider, such as {@code google}
   * @param accountId the id of the account at the provider
   * @return the link, or nothing when the account at the provider is linked to no account
   */
  public Future<Optional<Tables.ProviderLink>> find(String provider, String accountId) {
    return first(query().where(and(eq("provider", provider), eq("account_id", accountId))));
  }

  /**
   * Deletes the link of a factor, so that the account at the provider can be linked again.
   *
   * @param factor the name of the factor
   * @return a future that completes once the link is gone
   */
  public Future<Void> unlink(String factor) {
    return execute(delete().where(eq("factor", factor))).mapEmpty();
  }
}
