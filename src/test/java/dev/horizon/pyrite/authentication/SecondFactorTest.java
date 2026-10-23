package dev.horizon.pyrite.authentication;

import static dev.horizon.pyrite.Pyrite.ANNE;
import static dev.horizon.pyrite.Pyrite.status;
import static dev.horizon.pyrite.Pyrite.token;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.BaseEncoding;
import dev.horizon.pyrite.PyriteTest;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.Flow;
import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import dev.horizon.pyrite.authentication.v1.RecoveryCodeAnswer;
import dev.horizon.pyrite.authentication.v1.RecoveryCodes;
import dev.horizon.pyrite.authentication.v1.TotpAnswer;
import dev.horizon.pyrite.authentication.v1.TotpProvisioning;
import io.vertx.grpc.common.GrpcStatus;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SecondFactorTest extends PyriteTest {

  private static final String PASSWORD = "correct horse battery staple";

  @BeforeEach
  void open() throws Exception {
    pyrite.accounts();
    pyrite.account("anne", "anne@acme.dev", PASSWORD);
  }

  private static byte[] secret(String uri) {
    for (String parameter : URI.create(uri).getRawQuery().split("&")) {
      if (parameter.startsWith("secret=")) {
        return BaseEncoding.base32().decode(parameter.substring("secret=".length()));
      }
    }
    throw new AssertionError("The URI " + uri + " has no secret");
  }

  private String code(byte[] secret, long offset) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA1");
    mac.init(new SecretKeySpec(secret, "HmacSHA1"));
    byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(pyrite.clock.instant().getEpochSecond() / 30
        + offset).array());
    int at = hash[hash.length - 1] & 0xf;
    int binary = ((hash[at] & 0x7f) << 24) | ((hash[at + 1] & 0xff) << 16) | ((hash[at + 2] & 0xff) << 8)
        | (hash[at + 3] & 0xff);
    return String.format("%06d", binary % 1_000_000);
  }

  private byte[] enrolTotp() throws Exception {
    Flow enrolment = pyrite.start(ANNE, "enrol-totp", "");
    byte[] secret = secret(enrolment.getChallenges(0).getParameters().unpack(TotpProvisioning.class).getUri());
    pyrite.answer(enrolment, TotpAnswer.newBuilder().setCode(code(secret, 0)).build());
    pyrite.clock.move(Duration.ofSeconds(30));
    return secret;
  }

  private Flow afterPassword() throws Exception {
    return pyrite.answer(pyrite.start("", "sign-in-with-two-factors", "anne@acme.dev"), PasswordAnswer.newBuilder()
        .setPassword(PASSWORD).build());
  }

  private static TotpAnswer totp(String code) {
    return TotpAnswer.newBuilder().setCode(code).build();
  }

  @Nested
  @DisplayName("TOTP enrolment")
  class TotpEnrolment {

    @Test
    @DisplayName("the provisioning URI names the issuer, SHA-1, six digits and thirty seconds")
    void uri() throws Exception {
      String uri = pyrite.start(ANNE, "enrol-totp", "").getChallenges(0).getParameters().unpack(
          TotpProvisioning.class).getUri();

      assertThat(uri).startsWith("otpauth://totp/Acme:").contains("issuer=Acme", "algorithm=SHA1", "digits=6",
          "period=30");
      assertThat(secret(uri)).hasSize(20);
    }

    @Test
    @DisplayName("each enrolment gets its own secret")
    void fresh() throws Exception {
      String first = pyrite.start(ANNE, "enrol-totp", "").getChallenges(0).getParameters().unpack(
          TotpProvisioning.class).getUri();
      String second = pyrite.start(ANNE, "enrol-totp", "").getChallenges(0).getParameters().unpack(
          TotpProvisioning.class).getUri();

      assertThat(secret(first)).isNotEqualTo(secret(second));
    }

    @Test
    @DisplayName("a wrong first code activates nothing")
    void wrongFirst() throws Exception {
      Flow enrolment = pyrite.start(ANNE, "enrol-totp", "");
      byte[] secret = secret(enrolment.getChallenges(0).getParameters().unpack(TotpProvisioning.class).getUri());

      assertThat(status(() -> pyrite.answer(enrolment, totp("000000")))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
      pyrite.clock.move(Duration.ofSeconds(30));
      assertThat(status(() -> pyrite.answer(afterPassword(), totp(code(secret, 0))))).isEqualTo(
          GrpcStatus.PERMISSION_DENIED);
    }
  }

  @Nested
  @DisplayName("TOTP sign-in")
  class TotpSignIn {

    @Test
    @DisplayName("a password and a code sign in, and the password alone does not")
    void twoFactors() throws Exception {
      byte[] secret = enrolTotp();

      Flow flow = afterPassword();

      assertThat(flow.hasResult()).isFalse();
      assertThat(flow.getChallengesList()).extracting(Challenge::getMethod).contains(
          "pyrite.authentication.v1.TotpAnswer").doesNotContain("pyrite.authentication.v1.PasswordAnswer");
      assertThat(pyrite.authenticate(token(pyrite.answer(flow, totp(code(secret, 0))))).getSubject()).isEqualTo(ANNE);
    }

    @Test
    @DisplayName("a code from one step either side is taken, and from two steps is not")
    void window() throws Exception {
      byte[] secret = enrolTotp();

      assertThat(status(() -> pyrite.answer(afterPassword(), totp(code(secret, -2))))).isEqualTo(
          GrpcStatus.PERMISSION_DENIED);
      assertThat(status(() -> pyrite.answer(afterPassword(), totp(code(secret, 2))))).isEqualTo(
          GrpcStatus.PERMISSION_DENIED);
      assertThat(token(pyrite.answer(afterPassword(), totp(code(secret, 1))))).isNotEmpty();
    }

    @Test
    @DisplayName("a code is taken once, and an older code after it is not")
    void replay() throws Exception {
      byte[] secret = enrolTotp();
      String current = code(secret, 0);
      String previous = code(secret, -1);
      pyrite.answer(afterPassword(), totp(current));

      assertThat(status(() -> pyrite.answer(afterPassword(), totp(current)))).isEqualTo(
          GrpcStatus.PERMISSION_DENIED);
      assertThat(status(() -> pyrite.answer(afterPassword(), totp(previous)))).isEqualTo(
          GrpcStatus.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("a code that is not six digits is wrong")
    void malformed() throws Exception {
      enrolTotp();

      for (String code : new String[] { "", "12345", "1234567", "abcdef", " 123456" }) {
        assertThat(status(() -> pyrite.answer(afterPassword(), totp(code)))).as(code).isEqualTo(
            GrpcStatus.PERMISSION_DENIED);
      }
    }

    @Test
    @DisplayName("a code of another account's secret is wrong")
    void otherSecret() throws Exception {
      enrolTotp();

      assertThat(status(() -> pyrite.answer(afterPassword(), totp(code(new byte[20], 0))))).isEqualTo(
          GrpcStatus.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("two answers of the same kind are not two factors")
    void sameKind() throws Exception {
      Flow flow = afterPassword();

      assertThat(status(() -> pyrite.answer(flow, PasswordAnswer.newBuilder().setPassword(PASSWORD).build())))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }

  @Nested
  @DisplayName("Recovery codes")
  class Recovery {

    private List<String> codes() throws Exception {
      return pyrite.start(ANNE, "enrol-recovery-codes", "").getResult().getIssued(0).unpack(RecoveryCodes.class)
          .getCodesList();
    }

    private static RecoveryCodeAnswer recovery(String code) {
      return RecoveryCodeAnswer.newBuilder().setCode(code).build();
    }

    @Test
    @DisplayName("an enrolment issues ten different codes")
    void issued() throws Exception {
      List<String> codes = codes();

      assertThat(codes).hasSize(10);
      assertThat(new HashSet<>(codes)).hasSize(10);
    }

    @Test
    @DisplayName("each code is a second factor once")
    void once() throws Exception {
      List<String> codes = codes();

      assertThat(token(pyrite.answer(afterPassword(), recovery(codes.get(0))))).isNotEmpty();
      assertThat(status(() -> pyrite.answer(afterPassword(), recovery(codes.get(0))))).isEqualTo(
          GrpcStatus.PERMISSION_DENIED);
      assertThat(token(pyrite.answer(afterPassword(), recovery(codes.get(1))))).isNotEmpty();
    }

    @Test
    @DisplayName("new codes replace the old ones")
    void replaced() throws Exception {
      List<String> old = codes();
      List<String> fresh = codes();

      assertThat(status(() -> pyrite.answer(afterPassword(), recovery(old.get(0))))).isEqualTo(
          GrpcStatus.PERMISSION_DENIED);
      assertThat(token(pyrite.answer(afterPassword(), recovery(fresh.get(0))))).isNotEmpty();
    }

    @Test
    @DisplayName("a code that was not issued is wrong")
    void forged() throws Exception {
      String code = codes().get(0);

      for (String guess : new String[] { "", code.toUpperCase(), code.substring(1), code + "a", "0000000000" }) {
        assertThat(status(() -> pyrite.answer(afterPassword(), recovery(guess)))).as(guess).isEqualTo(
            GrpcStatus.PERMISSION_DENIED);
      }
    }

    @Test
    @DisplayName("a code of another account is wrong")
    void otherAccount() throws Exception {
      pyrite.account("bob", "bob@acme.dev", "another long passphrase");
      String bobs = pyrite.start("user:bob", "enrol-recovery-codes", "").getResult().getIssued(0).unpack(
          RecoveryCodes.class).getCodes(0);
      codes();

      assertThat(status(() -> pyrite.answer(afterPassword(), recovery(bobs)))).isEqualTo(
          GrpcStatus.PERMISSION_DENIED);
    }
  }
}
