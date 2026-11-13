package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Struct;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.crypto.v1.Expected;
import dev.horizon.pyrite.crypto.v1.VerifyRequest;
import dev.horizon.pyrite.crypto.v1.VerifyResponse;
import io.vertx.core.json.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Checks the access token of the last session as a service sees it: a JWT of type {@code at+jwt} that the access token
 * key verifies, issued by the issuer of the options to the subject of the account, for the session, for five minutes.
 */
public record ExpectAccessTokenClaims(String subject) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    String token = environment.get(Value.ACCESS_TOKEN);
    String[] parts = token.split("\\.");
    JsonObject header = new JsonObject(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
    assertThat(header.getString("typ")).as("typ").isEqualTo("at+jwt");
    VerifyResponse verdict = environment.pyrite().cryptography.verify(VerifyRequest.newBuilder().setSubject(subject)
        .setKey("keys/access").setToken(token).setExpected(Expected.newBuilder().setType("at+jwt").setIssuer("Acme"))
        .build()).await(TIMEOUT);
    assertThat(verdict.hasVerified()).as("verified: " + verdict).isTrue();
    Struct claims = verdict.getVerified().getClaims();
    assertThat(claims.getFieldsOrThrow("sub").getStringValue()).as("sub").isEqualTo(subject);
    assertThat(claims.getFieldsOrThrow("sid").getStringValue()).as("sid").isEqualTo(environment.get(Value.SESSION));
    assertThat(claims.getFieldsOrThrow("exp").getNumberValue() - claims.getFieldsOrThrow("iat").getNumberValue())
        .as("lifetime").isEqualTo(300);
    assertThat(claims.getFieldsOrThrow("jti").getStringValue()).as("jti").isNotEmpty();
  }
}
