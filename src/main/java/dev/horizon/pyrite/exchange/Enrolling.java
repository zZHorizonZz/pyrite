package dev.horizon.pyrite.exchange;

import com.google.protobuf.Message;
import dev.horizon.pyrite.schema.v1.Stage;

/**
 * A verifier that adds a credential to the account of the exchange: a right answer enrols it as pending, and the
 * exchange activates it when it ends.
 *
 * @param <A> the message of the answer
 */
sealed interface Enrolling<A extends Message> extends Verifier<A>
    permits PasswordEnrolment, TotpEnrolment, PasskeyEnrolment, ProviderEnrolment {

  /**
   * Gives the factor that a stage names to enrol with this verifier, such as {@code PASSWORD}.
   */
  Stage.Factor enrols();
}
