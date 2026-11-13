package dev.horizon.pyrite.exchange;

import dev.horizon.pyrite.authentication.v1.Delivery;
import java.util.List;

/**
 * What the start of an exchange or a right answer gives back before the database writes the exchange: the response,
 * which still needs the times that the database writes, and the codes to deliver.
 *
 * @param message the response, with the challenges of the stage that the exchange reached or the result of its end
 * @param deliveries the codes that the stage made, without the time that they expire
 */
record Step(dev.horizon.pyrite.authentication.v1.Exchange.Builder message, List<Delivery> deliveries) {
}
