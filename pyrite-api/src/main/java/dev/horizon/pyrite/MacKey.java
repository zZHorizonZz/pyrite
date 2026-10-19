package dev.horizon.pyrite;

import io.vertx.core.Future;
import java.util.List;

/**
 * A key that fingerprints what an application only compares, such as one-time codes, recovery codes or refresh
 * tokens, so that it stores the fingerprint instead of the credential. Its algorithm is HMAC-SHA256.
 */
public non-sealed interface MacKey extends Key {

  @Override
  Mac purpose();

  /**
   * @return the fingerprint of {@code text} under the primary version, to store; it names the version that made it
   */
  Future<String> fingerprint(String text);

  /**
   * @return the fingerprint of {@code text} under every live version, the primary first, to look up a credential
   * that was stored under any of them
   */
  Future<List<String>> fingerprints(String text);
}
