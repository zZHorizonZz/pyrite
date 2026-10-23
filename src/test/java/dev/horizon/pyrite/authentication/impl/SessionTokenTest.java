package dev.horizon.pyrite.authentication.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.horizon.pyrite.PyriteException;
import io.vertx.grpc.common.GrpcStatus;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SessionTokenTest {

  @Test
  @DisplayName("a generated token parses back to its id and secret")
  void roundTrip() {
    SessionToken token = SessionToken.generate();

    assertThat(SessionToken.parse(token.toString())).isEqualTo(token);
  }

  @Test
  @DisplayName("a secret has 32 random bytes, and no two tokens share one")
  void random() {
    Set<String> secrets = new HashSet<>();
    for (int index = 0; index < 1000; index++) {
      secrets.add(SessionToken.generate().secret());
    }

    assertThat(secrets).hasSize(1000);
    assertThat(SessionToken.generate().secret()).hasSize(43);
  }

  @ParameterizedTest
  @ValueSource(strings = { "", ".", "id", "id.", ".secret" })
  @DisplayName("text without an id and a secret is refused as unauthenticated")
  void malformed(String text) {
    assertThatThrownBy(() -> SessionToken.parse(text)).isInstanceOfSatisfying(PyriteException.class,
        failure -> assertThat(failure.status()).isEqualTo(GrpcStatus.UNAUTHENTICATED));
  }
}
