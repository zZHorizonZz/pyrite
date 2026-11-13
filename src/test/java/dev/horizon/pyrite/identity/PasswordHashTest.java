package dev.horizon.pyrite.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.horizon.pyrite.Argon2Options;
import dev.horizon.pyrite.PyriteException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordHashTest {

  private static final Argon2Options CHEAP = new Argon2Options().setMemory(1024).setIterations(1).setParallelism(1);

  @Test
  @DisplayName("a hash matches its password and nothing else")
  void matches() {
    PasswordHash hash = PasswordHash.of("correct horse battery staple", CHEAP);

    assertThat(hash.matches("correct horse battery staple")).isTrue();
    for (String other : new String[] { "", "correct horse battery stapl", "Correct horse battery staple",
        "correct horse battery staple ", "correct horse battery staple\u0000" }) {
      assertThat(hash.matches(other)).as(other).isFalse();
    }
  }

  @Test
  @DisplayName("the same password hashes differently each time, and never holds the password")
  void salted() {
    PasswordHash first = PasswordHash.of("correct horse battery staple", CHEAP);
    PasswordHash second = PasswordHash.of("correct horse battery staple", CHEAP);

    assertThat(first.encoded()).isNotEqualTo(second.encoded()).doesNotContain("correct horse");
    assertThat(second.matches("correct horse battery staple")).isTrue();
  }

  @Test
  @DisplayName("a hash records its cost, so a later cost still matches an older hash")
  void cost() {
    PasswordHash old = PasswordHash.of("correct horse battery staple", CHEAP);

    assertThat(old.encoded()).contains("argon2id");
    assertThat(PasswordHash.of("another long passphrase", new Argon2Options()).matches(
        "another long passphrase")).isTrue();
    assertThat(old.matches("correct horse battery staple")).isTrue();
  }

  @Test
  @DisplayName("Unicode passwords match exactly")
  void unicode() {
    PasswordHash hash = PasswordHash.of("pässwörd-密码-🔑", CHEAP);

    assertThat(hash.matches("pässwörd-密码-🔑")).isTrue();
    assertThat(hash.matches("passwörd-密码-🔑")).isFalse();
  }

  @ParameterizedTest
  @ValueSource(ints = { 0, 7, 129 })
  @DisplayName("a password outside 8 to 128 characters is refused")
  void length(int length) {
    assertThatThrownBy(() -> PasswordHash.of("x".repeat(length), CHEAP)).isInstanceOf(PyriteException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = { "", "argon2id", "$argon2id$v=19$m=1024,t=1,p=1$", "not a hash" })
  @DisplayName("a stored value that is not a hash matches nothing")
  void corrupt(String encoded) {
    assertThat(new PasswordHash(encoded).matches("correct horse battery staple")).isFalse();
  }
}
