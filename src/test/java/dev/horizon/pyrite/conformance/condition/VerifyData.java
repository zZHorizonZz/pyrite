package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import com.google.protobuf.ByteString;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.crypto.v1.Detached;
import dev.horizon.pyrite.crypto.v1.VerifyRequest;

/**
 * Verifies a signature of data with a key, and with a version of it when the version is not empty, as a subject,
 * and leaves the verdict.
 */
public record VerifyData(String subject, String key, String data, Value<ByteString> signature, String version)
    implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.put(Value.VERIFICATION, environment.pyrite().cryptography.verify(VerifyRequest.newBuilder()
        .setSubject(subject).setKey(key).setDetached(Detached.newBuilder().setData(ByteString.copyFromUtf8(data))
            .setSignature(environment.get(signature)).setKeyVersion(version))
        .build()).await(TIMEOUT));
  }
}
