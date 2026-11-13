package dev.horizon.pyrite.exchange;

import static dev.horizon.basepoint.expression.Expression.add;
import static dev.horizon.basepoint.expression.Expression.now;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import dev.horizon.basepoint.expression.Expression;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.authentication.v1.ProviderAccount;
import dev.horizon.pyrite.identity.Credential;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.schema.v1.ExchangeDefinition;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.GrpcStatus;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * An exchange under way, as the database keeps it: the definition that it follows, who it is for, how far it got, the
 * nonce of its stage, what its verifiers keep for the next answer, and the credential that it enrols. {@link
 * ExchangeType} moves it on.
 *
 * <p>
 * Each stage that the exchange reaches gets a new random nonce, which expires after the lifetime of codes on the clock
 * of the database: a passkey answer signs it, and the state that the verifiers keep, such as the MAC of a code, lasts
 * as long as it.
 *
 * <p>
 * A right answer moves the exchange to its next revision; a wrong one counts against it and leaves it at its revision.
 * Two answers to the same revision cannot both win: the database stores an exchange only over the revision that it
 * read.
 */
final class Exchange {

  private static final int NONCE_BYTES = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final ExchangeDefinition definition;
  private final Tables.Exchange.Builder row;
  private final Map<String, Expression> times;
  private final boolean live;
  private Claimant claimant;
  private final JsonObject state;
  private boolean nonceLive;
  private Progress progress;

  /**
   * Creates an exchange of a row.
   *
   * @param times the times that the database writes with the row
   * @param live whether the exchange has not expired, on the clock of the database
   * @param nonceLive whether the nonce of the stage has not expired, on the clock of the database
   */
  Exchange(ExchangeDefinition definition, Tables.Exchange.Builder row, Map<String, Expression> times, boolean live,
      boolean nonceLive, Claimant claimant) {
    this.definition = definition;
    this.row = row;
    this.times = times;
    this.live = live;
    this.nonceLive = nonceLive;
    this.claimant = claimant;
    this.state = row.getState().isEmpty() ? new JsonObject() : new JsonObject(row.getState());
    this.progress = new Progress(definition, row.getStage(), Evidence.of(new JsonObject(row.getProofs())));
  }

  String id() {
    return row.getId();
  }

  String name() {
    return ExchangeType.EXCHANGES + row.getId();
  }

  ExchangeDefinition definition() {
    return definition;
  }

  long revision() {
    return row.getRevision();
  }

  Claimant claimant() {
    return claimant;
  }

  Progress progress() {
    return progress;
  }

  /**
   * Adds what an answer proved, and moves past every stage that the evidence then meets.
   *
   * @return whether the exchange moved to another stage, or past the last
   */
  boolean pass(Evidence gain) {
    Progress next = progress.pass(gain);
    boolean moved = next.stage() != progress.stage();
    progress = next;
    row.setStage(next.stage()).setProofs(next.evidence().json().encode()).setDone(next.isDone());
    return moved;
  }

  /**
   * Gives the nonce of the stage, if it has not expired.
   */
  Optional<byte[]> nonce() {
    return row.hasNonce() && nonceLive ? Optional.of(row.getNonce().toByteArray()) : Optional.empty();
  }

  /**
   * Gives the stage a new nonce, which expires after a lifetime from when the database writes the exchange, and
   * forgets what the verifiers kept for the stage before.
   */
  void renewNonce(Duration lifetime) {
    byte[] nonce = new byte[NONCE_BYTES];
    RANDOM.nextBytes(nonce);
    row.setNonce(ByteString.copyFrom(nonce)).clearNonceExpireTime();
    times.put("nonce_expire_time", add(now(), lifetime));
    nonceLive = true;
    state.clear();
    row.setState(state.encode());
  }

  /**
   * Gives what a verifier kept for the stage.
   */
  Optional<ByteString> state(Verifier<?> verifier) {
    return Optional.ofNullable(state.getString(verifier.name()))
        .map(encoded -> ByteString.copyFrom(Base64.getDecoder().decode(encoded)));
  }

  /**
   * Keeps something of a verifier for the stage, in place of what it kept before.
   */
  void keep(Verifier<?> verifier, ByteString value) {
    state.put(verifier.name(), Base64.getEncoder().encodeToString(value.toByteArray()));
    row.setState(state.encode());
  }

  /**
   * Forgets what a verifier kept, such as a code that worked once.
   */
  void forget(Verifier<?> verifier) {
    state.remove(verifier.name());
    row.setState(state.encode());
  }

  /**
   * Makes the exchange one of an account that an answer named, such as the account that links a provider account.
   */
  void identify(String account) {
    row.setAccount(account);
    claimant = claimant.withAccount(account);
  }

  /**
   * Gives the provider account that the exchange creates an account for, which no account links.
   */
  Optional<ProviderAccount> providerAccount() {
    if (!row.hasProviderAccount()) {
      return Optional.empty();
    }
    try {
      return Optional.of(ProviderAccount.parseFrom(row.getProviderAccount()));
    } catch (InvalidProtocolBufferException unreadable) {
      throw new IllegalStateException("The exchange " + row.getId() + " holds a provider account that is not one",
          unreadable);
    }
  }

  /**
   * Keeps a provider account that no account links, for {@code CREATE_ACCOUNT}.
   */
  void providerAccount(ProviderAccount account) {
    row.setProviderAccount(account.toByteString());
  }

  /**
   * Gives the name of the credential that the exchange enrols, pending until the exchange ends.
   */
  Optional<String> enrolment() {
    return row.hasFactor() ? Optional.of(row.getFactor()) : Optional.empty();
  }

  void enrol(Credential<?> credential) {
    row.setFactor(credential.name());
  }

  /**
   * Sets the time that the exchange expires: its lifetime from when the database first writes it.
   */
  void expireAfter(Duration lifetime) {
    times.put("expire_time", add(now(), lifetime));
  }

  /**
   * Takes an answer: the exchange moves to its next revision, under the id of the request, which a later request with
   * the same id gets the response of again.
   */
  void take(String requestId) {
    row.setRevision(row.getRevision() + 1).setRequestId(requestId);
  }

  /**
   * Tells why the exchange refuses an answer, if it does.
   *
   * @param revision the revision that the answer names
   * @param maxAnswers the most wrong answers that an exchange takes
   * @return {@code FAILED_PRECONDITION} when the exchange is done or expired, {@code ABORTED} when it is at another
   * revision, or {@code RESOURCE_EXHAUSTED} after the most wrong answers; nothing when it takes the answer
   */
  Optional<PyriteException> refusal(long revision, int maxAnswers) {
    if (progress.isDone()) {
      return Optional.of(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The exchange " + name() + " is done."));
    }
    if (!live) {
      return Optional.of(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The exchange " + name() + " expired."));
    }
    if (row.getRevision() != revision) {
      return Optional.of(new PyriteException(GrpcStatus.ABORTED, "The exchange " + name() + " is at revision "
          + row.getRevision() + ", not " + revision + "."));
    }
    if (row.getAnswers() >= maxAnswers) {
      return Optional.of(new PyriteException(GrpcStatus.RESOURCE_EXHAUSTED, "The exchange " + name() + " took "
          + maxAnswers + " answers; start another."));
    }
    return Optional.empty();
  }

  /**
   * Gives the response to the last right answer again, for a request with its id.
   *
   * @param requestId the id of the request; an empty id never repeats
   */
  Optional<dev.horizon.pyrite.authentication.v1.Exchange> replay(String requestId) {
    if (requestId.isEmpty() || !requestId.equals(row.getRequestId())) {
      return Optional.empty();
    }
    try {
      return Optional.of(dev.horizon.pyrite.authentication.v1.Exchange.parseFrom(row.getResponse()));
    } catch (InvalidProtocolBufferException unreadable) {
      throw new IllegalStateException("The exchange " + row.getId() + " holds a response that is not an exchange",
          unreadable);
    }
  }

  /**
   * Gives the row of the exchange as it is now, for the database.
   */
  Tables.Exchange row() {
    return row.build();
  }

  /**
   * Gives the times that the database writes with the row, as expressions of its clock.
   */
  Map<String, Expression> times() {
    return times;
  }
}
