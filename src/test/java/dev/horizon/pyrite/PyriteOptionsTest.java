package dev.horizon.pyrite;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.vertx.core.Vertx;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import java.time.Duration;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
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
    copy.getAuthenticationOptions().getArgon2Options().setMemory(1024);

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
    assertThat(options.missing()).containsExactly("relyingPartyOptions");

    options.getRelyingPartyOptions().addOrigin("https://acme.dev");
    assertThat(options.missing()).isEmpty();
  }

  @Test
  @DisplayName("every way in of a provider refuses one that lacks a part, or has the name of another")
  void providers() {
    ProviderOptions nameless = new ProviderOptions().setRedirectUri("https://acme.dev/callback");
    assertThatThrownBy(() -> new AuthenticationOptions().addProvider(nameless))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AuthenticationOptions().setProviders(List.of(nameless)))
        .isInstanceOf(IllegalArgumentException.class);

    AuthenticationOptions options = new AuthenticationOptions();
    assertThatThrownBy(() -> options.getProviders().add(nameless)).isInstanceOf(UnsupportedOperationException.class);
    assertThat(options.getProviders()).isEmpty();
  }

  @Test
  @DisplayName("two providers of the same name are refused, added one by one or set together")
  void providerName() throws Exception {
    Vertx vertx = Vertx.vertx();
    try {
      OAuth2Auth auth = OAuth2Auth.create(vertx, new OAuth2Options().setSite("https://provider.example")
          .setClientId("pyrite"));
      ProviderOptions google = new ProviderOptions().setName("google").setAuth(auth)
          .setRedirectUri("https://acme.dev/callback/google");
      ProviderOptions other = new ProviderOptions(google).setRedirectUri("https://acme.dev/callback/other");

      AuthenticationOptions options = new AuthenticationOptions().addProvider(google);
      assertThatThrownBy(() -> options.addProvider(other)).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new AuthenticationOptions().setProviders(List.of(google, other)))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(options.getProviders()).extracting(ProviderOptions::getRedirectUri)
          .containsExactly("https://acme.dev/callback/google");
    } finally {
      vertx.close().await(TIMEOUT);
    }
  }

  @Test
  @DisplayName("the memory of Argon2 is at least 8 KiB per lane, whichever setter changes the memory or the lanes")
  void argon2Memory() {
    assertThatThrownBy(() -> new Argon2Options().setMemory(16)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Argon2Options().setMemory(32).setParallelism(5))
        .isInstanceOf(IllegalArgumentException.class);

    Argon2Options argon2 = new Argon2Options().setParallelism(2).setMemory(16);
    assertThat(argon2.getMemory()).isEqualTo(16);
    assertThat(argon2.getParallelism()).isEqualTo(2);
  }

  @Test
  @DisplayName("the page token key is an AES key of 128, 192 or 256 bits")
  void pageTokenKey() {
    PyriteOptions options = new PyriteOptions();

    options.setPageTokenKey(new SecretKeySpec(new byte[16], "AES"));
    options.setPageTokenKey(new SecretKeySpec(new byte[32], "AES"));
    assertThatThrownBy(() -> options.setPageTokenKey(new SecretKeySpec(new byte[8], "AES")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> options.setPageTokenKey(new SecretKeySpec(new byte[32], "HmacSHA256")))
        .isInstanceOf(IllegalArgumentException.class);
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
