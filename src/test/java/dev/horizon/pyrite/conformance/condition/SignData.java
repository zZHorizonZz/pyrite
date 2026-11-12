package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import com.google.protobuf.ByteString;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.crypto.v1.SignRequest;

/**
 * Signs data with a key, as a subject, and keeps the signature.
 */
public record SignData(String subject, String key, String data, Value<ByteString> into) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.put(into, environment.pyrite().cryptography.sign(SignRequest.newBuilder().setSubject(subject)
        .setKey(key).setData(ByteString.copyFromUtf8(data)).build()).await(TIMEOUT).getSignature());
  }
}
