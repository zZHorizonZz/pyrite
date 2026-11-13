package dev.horizon.pyrite.conformance;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import com.google.protobuf.Any;
import com.google.protobuf.Message;
import dev.horizon.pyrite.PyriteFixture;
import dev.horizon.pyrite.authentication.v1.ContinueExchangeRequest;
import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.authentication.v1.Exchange;
import dev.horizon.pyrite.authentication.v1.StartExchangeRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The state of one run of a module: the Pyrite that it runs on, its variant, the values that its steps left, and the
 * codes that Pyrite sent, which the variant gets from the responses of exchanges or from an interceptor.
 */
public final class Environment {

  private final PyriteFixture pyrite;
  private final Variant variant;
  private final Map<Value<?>, Object> values = new HashMap<>();
  private final List<Delivery> intercepted = new CopyOnWriteArrayList<>();
  private final Map<String, Delivery> codes = new HashMap<>();
  private List<Delivery> sent = List.of();

  Environment(PyriteFixture pyrite, Variant variant) {
    this.pyrite = pyrite;
    this.variant = variant;
    if (variant.delivery() == CodeDelivery.INTERCEPTOR) {
      pyrite.service.addCodeInterceptor(context -> {
        intercepted.add(context.delivery());
        context.next();
      });
    }
  }

  public PyriteFixture pyrite() {
    return pyrite;
  }

  public Variant variant() {
    return variant;
  }

  /**
   * Gives a value that a step left.
   *
   * @throws AssertionError when no step left it
   */
  @SuppressWarnings("unchecked")
  public <T> T get(Value<T> value) {
    Object found = values.get(value);
    if (found == null) {
      throw new AssertionError("No step left " + value.name());
    }
    return (T) found;
  }

  public <T> void put(Value<T> value, T found) {
    values.put(value, found);
  }

  /**
   * Starts an exchange, which becomes the last exchange.
   */
  public Exchange start(StartExchangeRequest request) throws Exception {
    int before = intercepted.size();
    sent = List.of();
    Exchange exchange = pyrite.authentication.startExchange(request).await(TIMEOUT);
    keep(exchange, before);
    put(Value.EXCHANGE_START, exchange);
    return exchange;
  }

  /**
   * Answers the last exchange at its revision; the exchange that the answer gives becomes the last exchange.
   */
  public Exchange answer(Message answer) throws Exception {
    return answer(Any.pack(answer));
  }

  /**
   * Answers the last exchange at its revision with an answer as it is packed, which may be no answer at all.
   */
  public Exchange answer(Any answer) throws Exception {
    Exchange exchange = get(Value.EXCHANGE);
    return continueExchange(
        ContinueExchangeRequest.newBuilder().setExchange(exchange.getName()).setRevision(exchange.getRevision())
            .setRequestId(UUID.randomUUID().toString()).setAnswer(answer).build());
  }

  /**
   * Sends an answer as a request names it; the exchange that it gives becomes the last exchange.
   */
  public Exchange continueExchange(ContinueExchangeRequest request) throws Exception {
    int before = intercepted.size();
    sent = List.of();
    Exchange exchange = pyrite.authentication.continueExchange(request).await(TIMEOUT);
    keep(exchange, before);
    return exchange;
  }

  /**
   * Gives the codes that the last start or answer of an exchange sent, the way the variant gets them.
   */
  public List<Delivery> sent() {
    return sent;
  }

  /**
   * Gives the last code that Pyrite sent for an exchange.
   *
   * @throws AssertionError when Pyrite sent none
   */
  public Delivery code(Exchange exchange) {
    Delivery delivery = codes.get(exchange.getName());
    if (delivery == null) {
      throw new AssertionError("Pyrite sent no code for " + exchange.getName());
    }
    return delivery;
  }

  private void keep(Exchange exchange, int before) {
    sent = variant.delivery() == CodeDelivery.RESPONSE ? exchange.getDeliveriesList()
        : List.copyOf(intercepted.subList(before, intercepted.size()));
    if (!sent.isEmpty()) {
      codes.put(exchange.getName(), sent.getLast());
    }
    put(Value.EXCHANGE, exchange);
  }
}
