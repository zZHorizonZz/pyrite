package dev.horizon.pyrite.authentication.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TotpSecretTest {

  private static final TotpSecret RFC = new TotpSecret("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

  @Nested
  @DisplayName("Codes")
  class Codes {

    @ParameterizedTest(name = "at {0}")
    @CsvSource({ "59, 287082", "1111111109, 081804", "1111111111, 050471", "1234567890, 005924",
        "2000000000, 279037", "20000000000, 353130" })
    @DisplayName("a code matches the SHA-1 vectors of RFC 6238, cut to six digits")
    void vectors(long seconds, String code) {
      assertThat(RFC.code(TotpSecret.step(Instant.ofEpochSecond(seconds)))).isEqualTo(code);
    }

    @Test
    @DisplayName("each generated secret has 20 random bytes")
    void generated() {
      assertThat(TotpSecret.generate().bytes()).hasSize(20).isNotEqualTo(TotpSecret.generate().bytes());
    }
  }

  @Nested
  @DisplayName("Matching")
  class Matching {

    private final Instant now = Instant.ofEpochSecond(1111111111);
    private final long step = TotpSecret.step(now);

    @Test
    @DisplayName("a code of the current step or one either side matches, and names its step")
    void window() {
      assertThat(RFC.match(RFC.code(step), now, 0)).isEqualTo(OptionalLong.of(step));
      assertThat(RFC.match(RFC.code(step - 1), now, 0)).isEqualTo(OptionalLong.of(step - 1));
      assertThat(RFC.match(RFC.code(step + 1), now, 0)).isEqualTo(OptionalLong.of(step + 1));
    }

    @Test
    @DisplayName("a code two steps away does not match")
    void outside() {
      assertThat(RFC.match(RFC.code(step - 2), now, 0)).isEmpty();
      assertThat(RFC.match(RFC.code(step + 2), now, 0)).isEmpty();
    }

    @Test
    @DisplayName("a code of a step already used, or older, does not match")
    void replay() {
      assertThat(RFC.match(RFC.code(step), now, step)).isEmpty();
      assertThat(RFC.match(RFC.code(step - 1), now, step)).isEmpty();
      assertThat(RFC.match(RFC.code(step + 1), now, step)).isEqualTo(OptionalLong.of(step + 1));
    }

    @Test
    @DisplayName("text that is not a code does not match")
    void malformed() {
      for (String code : new String[] { "", "1", RFC.code(step) + "0", RFC.code(step).substring(1), "abcdef" }) {
        assertThat(RFC.match(code, now, 0)).as(code).isEmpty();
      }
    }
  }

  @Test
  @DisplayName("the URI names the issuer, the label and the parameters, and encodes spaces")
  void uri() {
    assertThat(RFC.uri("Acme Corp", "anne@acme.dev")).isEqualTo("otpauth://totp/Acme%20Corp:anne%40acme.dev"
        + "?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=Acme%20Corp&algorithm=SHA1&digits=6&period=30");
  }
}
