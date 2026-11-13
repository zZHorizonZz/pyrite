package dev.horizon.pyrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PyriteOptionsTest {

  @Test
  @DisplayName("a copy is a copy all the way down, so a change of either does not reach the other")
  void copy() {
    PyriteOptions options = new PyriteOptions().setAuthenticationOptions(new AuthenticationOptions()
        .setRelyingPartyOptions(new RelyingPartyOptions().addOrigin("https://acme.dev")));
    PyriteOptions copy = new PyriteOptions(options);

    copy.setDefaultPageSize(7).getKeyOptions().setClockSkew(Duration.ZERO);
    copy.getAuthenticationOptions().setMaxAnswers(1).getRelyingPartyOptions().addOrigin("https://evil.example");
    copy.getAuthenticationOptions().getArgon2Options().setMemory(1);

    assertThat(options.getDefaultPageSize()).isEqualTo(PyriteOptions.DEFAULT_DEFAULT_PAGE_SIZE);
    assertThat(options.getKeyOptions().getClockSkew()).isEqualTo(KeyOptions.DEFAULT_CLOCK_SKEW);
    assertThat(options.getAuthenticationOptions().getMaxAnswers()).isEqualTo(AuthenticationOptions.DEFAULT_MAX_ANSWERS);
    assertThat(options.getAuthenticationOptions().getRelyingPartyOptions().getOrigins())
        .containsExactly("https://acme.dev");
    assertThat(options.getAuthenticationOptions().getArgon2Options().getMemory())
        .isEqualTo(Argon2Options.DEFAULT_MEMORY);
    assertThat(copy.getPageTokenKey()).isSameAs(options.getPageTokenKey());
  }

  @Test
  @DisplayName("a setter refuses a value that no Pyrite can run with")
  void refused() {
    assertThatThrownBy(() -> new PyriteOptions().setMaxPageSize(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new KeyOptions().setClockSkew(Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AuthenticationOptions().setCodeLifetime(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Argon2Options().setParallelism(0)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("the options of authentication name what accounts need and they lack")
  void missing() {
    AuthenticationOptions options = new AuthenticationOptions();
    assertThat(options.missing()).containsExactly("macKey", "encryptionKey", "accessTokenKey", "issuer",
        "relyingPartyOptions");

    options.setMacKey("keys/sessions").setEncryptionKey("keys/factors").setAccessTokenKey("keys/access")
        .setIssuer("Acme")
        .setRelyingPartyOptions(new RelyingPartyOptions().setId("acme.dev").setName("Acme"));
    assertThat(options.missing()).isEmpty();
  }

  @Test
  @DisplayName("a request's page size falls back to the default, and never passes the largest page")
  void pageSize() {
    PyriteOptions options = new PyriteOptions().setDefaultPageSize(20).setMaxPageSize(100);

    assertThat(options.pageSize(0)).isEqualTo(20);
    assertThat(options.pageSize(50)).isEqualTo(50);
    assertThat(options.pageSize(500)).isEqualTo(100);
  }
}
