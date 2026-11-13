package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import com.google.protobuf.ByteString;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.crypto.v1.DecryptRequest;

/**
 * Decrypts a ciphertext with a key and associated data, as a subject, and leaves the plaintext.
 */
public record Decrypt(String subject, String key, Value<ByteString> ciphertext, String associatedData)
    implements Condition {

  /**
   * Decrypts with a key, as a subject.
   */
  public static ByteString call(Environment environment, String subject, String key, ByteString ciphertext,
      String associatedData) throws Exception {
    return environment.pyrite().cryptography.decrypt(DecryptRequest.newBuilder().setSubject(subject).setKey(key)
        .setCiphertext(ciphertext).setAssociatedData(ByteString.copyFromUtf8(associatedData)).build())
        .await(TIMEOUT).getPlaintext();
  }

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.put(Value.PLAINTEXT, call(environment, subject, key, environment.get(ciphertext), associatedData));
  }
}
