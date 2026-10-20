package dev.horizon.pyrite;

import io.vertx.core.Future;
import java.util.List;

/**
 * A key that computes HMAC-SHA256 MACs of credentials that an application stores only to compare, such as one-time
 * codes or refresh tokens.
 */
public non-sealed interface MacKey extends Key {

  @Override
  Mac purpose();

  /**
   * Returns the MAC of the text under the primary version, to store in place of the text. The result contains the
   * number of the version.
   */
  Future<String> fingerprint(String text);

  /**
   * Returns the MAC of the text under every live version, primary first. Look up each of them to find a credential that
   * was stored before a rotation.
   */
  Future<List<String>> fingerprints(String text);
}
