package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import com.google.protobuf.ListValue;
import com.google.protobuf.Struct;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.crypto.v1.SignRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Signs a JWT with a key, as a subject: of anne, issued by Acme's server for its API, expiring a number of seconds
 * from now, of a type, with some claims changed; a claim changed to {@link #ABSENT} is left out. Leaves the token.
 */
public record SignToken(String subject, String key, long expiresIn, String type, Map<String, Object> changes)
    implements Condition {

  public static final String ISSUER = "https://auth.acme.dev";
  public static final String AUDIENCE = "https://api.acme.dev";
  public static final String ABSENT = "(absent)";

  public SignToken(String subject, String key, long expiresIn, String type) {
    this(subject, key, expiresIn, type, Map.of());
  }

  @Override
  public void evaluate(Environment environment) throws Exception {
    Struct.Builder claims = Struct.newBuilder().putFields("iss", text(ISSUER)).putFields("aud", text(AUDIENCE))
        .putFields("sub", text(ANNE)).putFields("exp", com.google.protobuf.Value.newBuilder().setNumberValue(
            Instant.now().getEpochSecond() + expiresIn).build());
    changes.forEach((name, claim) -> {
      if (ABSENT.equals(claim)) {
        claims.removeFields(name);
      } else {
        claims.putFields(name, value(claim));
      }
    });
    environment.put(Value.TOKEN, environment.pyrite().cryptography.sign(SignRequest.newBuilder().setSubject(subject)
        .setKey(key).setClaims(claims).setType(type).build()).await(TIMEOUT).getToken());
  }

  private static com.google.protobuf.Value text(String text) {
    return com.google.protobuf.Value.newBuilder().setStringValue(text).build();
  }

  private static com.google.protobuf.Value value(Object claim) {
    return switch (claim) {
      case Number seconds -> com.google.protobuf.Value.newBuilder().setNumberValue(Instant.now().getEpochSecond()
          + seconds.longValue()).build();
      case List<?> texts -> com.google.protobuf.Value.newBuilder().setListValue(ListValue.newBuilder().addAllValues(
          texts.stream().map(Object::toString).map(SignToken::text).toList())).build();
      default -> text(claim.toString());
    };
  }
}
