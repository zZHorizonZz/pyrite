package dev.horizon.pyrite.exchange;

import com.google.protobuf.Message;
import dev.horizon.pyrite.identity.Credential;
import dev.horizon.pyrite.identity.Proof;
import io.vertx.core.Future;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Signing in with a credential of one kind of the account of the exchange: it asks each active credential of the kind
 * to verify the answer, and the first that does gives the evidence. An exchange without such a credential does the
 * work of one all the same, so that its answers take as long as those of any other, and every answer is wrong.
 *
 * @param <C> the kind of credential
 * @param <A> the message of its answer
 */
sealed class CredentialVerifier<C extends Credential<A>, A extends Message> implements Verifier<A>
    permits PasskeyVerifier {

  private static final byte[] NO_NONCE = new byte[0];

  private final Class<C> kind;
  private final A prototype;
  private final Evidence proves;
  private final Supplier<Future<Void>> absent;

  /**
   * Creates the verifier of a kind of credential.
   *
   * @param kind the class of the credential, such as {@code PasswordCredential.class}
   * @param prototype an empty answer, such as {@code PasswordAnswer.getDefaultInstance()}
   * @param proves the most that a right answer proves
   * @param absent the work that an account without a credential of the kind costs, such as hashing a decoy password
   */
  CredentialVerifier(Class<C> kind, A prototype, Evidence proves, Supplier<Future<Void>> absent) {
    this.kind = kind;
    this.prototype = prototype;
    this.proves = proves;
    this.absent = absent;
  }

  @Override
  public A prototype() {
    return prototype;
  }

  @Override
  public Evidence proves() {
    return proves;
  }

  @Override
  public Future<Optional<Evidence>> verify(Exchange exchange, A answer) {
    byte[] nonce = exchange.nonce().orElse(NO_NONCE);
    return exchange.claimant().credentials(kind).compose(credentials -> {
      if (credentials.isEmpty()) {
        return absent.get().map(Optional.<Evidence> empty());
      }
      Future<Set<Proof>> proven = Future.succeededFuture(Set.of());
      for (C credential : credentials) {
        proven = proven.compose(found -> found.isEmpty() ? credential.verify(answer, nonce)
            : Future.succeededFuture(found));
      }
      return proven.map(found -> found.isEmpty() ? Optional.<Evidence> empty()
          : Optional.of(Evidence.of(found, proves.phishingResistant())));
    });
  }
}
