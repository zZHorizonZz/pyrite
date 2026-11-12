package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import com.google.protobuf.ByteString;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.crypto.v1.EncryptRequest;
import dev.horizon.pyrite.crypto.v1.EncryptResponse;
import java.nio.charset.StandardCharsets;

/**
 * Encrypts a text with a key and associated data, as a subject, and leaves the response and its ciphertext.
 */
public record Encrypt(String subject, String key, String plaintext, String associatedData) implements Condition {

  /**
   * Encrypts with a key, as a subject.
   */
  public static EncryptResponse call(Environment environment, String subject, String key, byte[] plaintext,
      String associatedData) throws Exception {
    return environment.pyrite().cryptography.encrypt(EncryptRequest.newBuilder().setSubject(subject).setKey(key)
        .setPlaintext(ByteString.copyFrom(plaintext)).setAssociatedData(ByteString.copyFromUtf8(associatedData))
        .build()).await(TIMEOUT);
  }

  @Override
  public void evaluate(Environment environment) throws Exception {
    EncryptResponse response = call(environment, subject, key, plaintext.getBytes(StandardCharsets.UTF_8),
        associatedData);
    environment.put(Value.ENCRYPTION, response);
    environment.put(Value.CIPHERTEXT, response.getCiphertext());
  }
}
