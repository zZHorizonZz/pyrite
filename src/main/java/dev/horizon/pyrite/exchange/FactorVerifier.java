package dev.horizon.pyrite.exchange;

import com.google.protobuf.Message;
import dev.horizon.pyrite.identity.FactorService;
import dev.horizon.pyrite.identity.Proof;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Signing in with a factor of one kind of the account of the exchange: it checks the answer against each active factor
 * of the kind, and the first that takes it gives the evidence. An exchange without such a factor does the work of one
 * all the same, so that its answers take as long as those of any other, and every answer is wrong.
 *
 * @param <A> the message of its answer
 */
abstract sealed class FactorVerifier<A extends Message> implements Verifier<A>
    permits PasswordVerifier, TotpVerifier, RecoveryCodeVerifier, PasskeyVerifier {

  private static final byte[] NO_NONCE = new byte[0];

  final FactorService factors;
  private final Tables.Factor.Kind kind;
  private final A prototype;
  private final Evidence proves;

  /**
   * Creates the verifier of a kind of factor.
   *
   * @param kind the kind of the factors that take the answer
   * @param prototype an empty answer, such as {@code PasswordAnswer.getDefaultInstance()}
   * @param proves the most that a right answer proves
   */
  FactorVerifier(FactorService factors, Tables.Factor.Kind kind, A prototype, Evidence proves) {
    this.factors = factors;
    this.kind = kind;
    this.prototype = prototype;
    this.proves = proves;
  }

  @Override
  public A prototype() {
    return prototype;
  }

  @Override
  public Evidence proves() {
    return proves;
  }

  /**
   * Checks an answer against one factor, with what a right answer changes, such as a code that works once.
   *
   * @param nonce the nonce of the stage, or an empty array when it expired
   * @return the factors that the answer proves, or none for a wrong answer
   */
  abstract Future<Set<Proof>> check(Tables.Factor factor, A answer, byte[] nonce);

  /**
   * Does the work that an account without a factor of the kind costs, such as hashing a decoy password.
   */
  Future<Void> absent() {
    return Future.succeededFuture();
  }

  @Override
  public Future<Optional<Evidence>> verify(ExchangeRecord exchange, A answer) {
    byte[] nonce = exchange.nonce().orElse(NO_NONCE);
    return exchange.claimant().factors(kind).compose(found -> {
      if (found.isEmpty()) {
        return absent().map(Optional.<Evidence> empty());
      }
      return first(found, factor -> check(factor, answer, nonce)).map(proofs -> proofs.isEmpty()
          ? Optional.<Evidence> empty()
          : Optional.of(Evidence.of(proofs, proves.phishingResistant())));
    });
  }

  /**
   * Checks an answer against factors in turn, until one takes it.
   *
   * @return what the first factor that takes it proves, or nothing when none takes it
   */
  static Future<Set<Proof>> first(List<Tables.Factor> factors, Function<Tables.Factor, Future<Set<Proof>>> check) {
    Future<Set<Proof>> proofs = Future.succeededFuture(Set.of());
    for (Tables.Factor factor : factors) {
      proofs = proofs.compose(found -> found.isEmpty() ? check.apply(factor) : Future.succeededFuture(found));
    }
    return proofs;
  }
}
