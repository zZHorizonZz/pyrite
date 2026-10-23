package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.authentication.v1.IdentifierKind;
import dev.horizon.pyrite.impl.resource.Resource;
import io.vertx.core.Future;

/**
 * An identifier of an account, of the type {@code pyrite.dev/Identifier}: an email address, a phone number or a
 * username. Its kind and value are set at creation, and a {@code MARK_VERIFIED} flow verifies it.
 */
public interface Identifier extends Resource {

  /**
   * Gives the identifier's kind.
   *
   * @return the kind
   */
  IdentifierKind kind();

  /**
   * Gives the identifier's value, as Pyrite normalized it at creation.
   *
   * @return the value, such as {@code anne@acme.dev}
   */
  String value();

  /**
   * Tells whether a flow proved that the account receives codes at the identifier.
   *
   * @return whether it is verified
   */
  boolean isVerified();

  /**
   * Tells whether a code can be sent to the identifier.
   *
   * @return whether it is an active email address or phone number
   */
  boolean isDeliverable();

  /**
   * Gives the channel that a code goes by.
   *
   * @return email for an email address, SMS for a phone number
   */
  Delivery.Channel channel();

  /**
   * Gives the value with most of it hidden, to show the person where a code went.
   *
   * @return the masked value
   */
  String mask();

  /**
   * Marks the identifier as verified.
   *
   * @return the verified identifier
   */
  Future<Identifier> verify();
}
