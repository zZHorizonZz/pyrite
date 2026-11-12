package dev.horizon.pyrite.conformance;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import com.google.protobuf.Any;
import com.google.protobuf.Message;
import dev.horizon.pyrite.PyriteFixture;
import dev.horizon.pyrite.authentication.v1.ContinueFlowRequest;
import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.authentication.v1.Flow;
import dev.horizon.pyrite.authentication.v1.StartFlowRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The state of one run of a module: the Pyrite that it runs on, its variant, the values that its steps left, and the
 * codes that Pyrite sent, which the variant gets from the responses of flows or from an interceptor.
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
   * Starts a flow, which becomes the last flow.
   */
  public Flow start(StartFlowRequest request) throws Exception {
    int before = intercepted.size();
    sent = List.of();
    Flow flow = pyrite.authentication.startFlow(request).await(TIMEOUT);
    keep(flow, before);
    put(Value.FLOW_START, flow);
    return flow;
  }

  /**
   * Answers the last flow at its revision; the flow that the answer gives becomes the last flow.
   */
  public Flow answer(Message answer) throws Exception {
    return answer(Any.pack(answer));
  }

  /**
   * Answers the last flow at its revision with an answer as it is packed, which may be no answer at all.
   */
  public Flow answer(Any answer) throws Exception {
    Flow flow = get(Value.FLOW);
    return continueFlow(ContinueFlowRequest.newBuilder().setFlow(flow.getName()).setRevision(flow.getRevision())
        .setRequestId(UUID.randomUUID().toString()).setAnswer(answer).build());
  }

  /**
   * Sends an answer as a request names it; the flow that it gives becomes the last flow.
   */
  public Flow continueFlow(ContinueFlowRequest request) throws Exception {
    int before = intercepted.size();
    sent = List.of();
    Flow flow = pyrite.authentication.continueFlow(request).await(TIMEOUT);
    keep(flow, before);
    return flow;
  }

  /**
   * Gives the codes that the last start or answer of a flow sent, the way the variant gets them.
   */
  public List<Delivery> sent() {
    return sent;
  }

  /**
   * Gives the last code that Pyrite sent for a flow.
   *
   * @throws AssertionError when Pyrite sent none
   */
  public Delivery code(Flow flow) {
    Delivery delivery = codes.get(flow.getName());
    if (delivery == null) {
      throw new AssertionError("Pyrite sent no code for " + flow.getName());
    }
    return delivery;
  }

  private void keep(Flow flow, int before) {
    sent = variant.delivery() == CodeDelivery.RESPONSE ? flow.getDeliveriesList()
        : List.copyOf(intercepted.subList(before, intercepted.size()));
    if (!sent.isEmpty()) {
      codes.put(flow.getName(), sent.getLast());
    }
    put(Value.FLOW, flow);
  }
}
