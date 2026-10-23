package dev.horizon.pyrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.io.BaseEncoding;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import dev.horizon.pyrite.authentication.v1.AuthenticateRequest;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.CodeAnswer;
import dev.horizon.pyrite.authentication.v1.ContinueFlowRequest;
import dev.horizon.pyrite.authentication.v1.Flow;
import dev.horizon.pyrite.authentication.v1.IdentifierKind;
import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import dev.horizon.pyrite.authentication.v1.Principal;
import dev.horizon.pyrite.authentication.v1.RecoveryCodeAnswer;
import dev.horizon.pyrite.authentication.v1.RecoveryCodes;
import dev.horizon.pyrite.authentication.v1.SessionToken;
import dev.horizon.pyrite.authentication.v1.StartFlowRequest;
import dev.horizon.pyrite.authentication.v1.TotpAnswer;
import dev.horizon.pyrite.authentication.v1.TotpProvisioning;
import dev.horizon.pyrite.data.v1.CreateResourceRequest;
import dev.horizon.pyrite.data.v1.Resource;
import dev.horizon.pyrite.data.v1.Value;
import dev.horizon.pyrite.impl.model.TypeSystem;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcStatus;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthenticationTest {

  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
  private static final String ANNE = "user:anne";
  private static final String EMAIL = "anne@acme.dev";
  private static final String PASSWORD = "correct horse battery staple";

  private Installation installation;

  @BeforeEach
  void start() throws Exception {
    installation = Installation.start(START);
    create("", "acme.dev/Organization", "acme", Map.of());
    create("organizations/acme", TypeSystem.KEY, "sessions", Map.of("purpose", value("MAC"), "algorithm",
        value("HMAC_SHA256")));
    create("organizations/acme", TypeSystem.KEY, "factors", Map.of("purpose", value("ENCRYPTION"), "algorithm",
        value("AES256_GCM")));
    create("", TypeSystem.ACCOUNT, "anne", Map.of("kind", value("HUMAN")));
    create("accounts/anne", TypeSystem.IDENTIFIER, "work", Map.of("kind", value("EMAIL"), "value",
        value("Anne@Acme.dev")));
    Flow enrolment = start(ANNE, "enrol-password", "");
    answer(enrolment, PasswordAnswer.newBuilder().setPassword(PASSWORD).build());
  }

  @AfterEach
  void stop() throws Exception {
    installation.close();
  }

  @Test
  void aPasswordSignsInToASessionThatAuthenticates() throws Exception {
    Flow flow = start("", "sign-in", EMAIL);
    assertThat(flow.getChallengesList()).extracting(Challenge::getMethod)
        .contains("pyrite.authentication.v1.PasswordAnswer");

    String token = token(answer(flow, PasswordAnswer.newBuilder().setPassword(PASSWORD).build()));

    Principal principal = authenticate(token);
    assertThat(principal.getSubject()).isEqualTo(ANNE);
    assertThat(principal.getAccount()).isEqualTo("accounts/anne");
    assertThat(status(() -> authenticate(token.substring(0, token.length() - 1)))).isEqualTo(
        GrpcStatus.UNAUTHENTICATED);
  }

  @Test
  void aWrongAnswerKeepsTheRevisionAndCountsAgainstTheFlow() throws Exception {
    Flow flow = start("", "sign-in", EMAIL);
    PasswordAnswer wrong = PasswordAnswer.newBuilder().setPassword("not the password").build();

    for (int attempt = 0; attempt < 3; attempt++) {
      assertThat(status(() -> answer(flow, wrong))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }

    assertThat(status(() -> answer(flow, PasswordAnswer.newBuilder().setPassword(PASSWORD).build())))
        .isEqualTo(GrpcStatus.RESOURCE_EXHAUSTED);
  }

  @Test
  void anUnknownIdentifierGetsTheSameChallengesAndNoAnswerIsRight() throws Exception {
    Flow known = start("", "sign-in", EMAIL);
    Flow unknown = start("", "sign-in", "nobody@acme.dev");

    assertThat(unknown.getChallengesList()).extracting(Challenge::getMethod)
        .isEqualTo(known.getChallengesList().stream().map(Challenge::getMethod).toList());
    assertThat(status(() -> answer(unknown, PasswordAnswer.newBuilder().setPassword(PASSWORD).build())))
        .isEqualTo(GrpcStatus.PERMISSION_DENIED);
  }

  @Test
  void twoFactorsTakeAPasswordAndATotpCode() throws Exception {
    Flow enrolment = start(ANNE, "enrol-totp", "");
    byte[] secret = secret(enrolment.getChallenges(0).getParameters().unpack(TotpProvisioning.class).getUri());
    Flow enrolled = answer(enrolment, TotpAnswer.newBuilder().setCode(code(secret)).build());
    assertThat(enrolled.getResult().getResources(0).getFieldsMap().get("state").getStringValue())
        .isEqualTo("ACTIVE");
    installation.clock.move(Duration.ofSeconds(30));

    Flow flow = answer(start("", "sign-in-with-two-factors", EMAIL), PasswordAnswer.newBuilder()
        .setPassword(PASSWORD).build());
    assertThat(flow.hasResult()).isFalse();
    assertThat(flow.getChallengesList()).extracting(Challenge::getMethod)
        .contains("pyrite.authentication.v1.TotpAnswer").doesNotContain("pyrite.authentication.v1.PasswordAnswer");

    assertThat(authenticate(token(answer(flow, TotpAnswer.newBuilder().setCode(code(secret)).build())))
        .getSubject()).isEqualTo(ANNE);
  }

  @Test
  void aRecoveryCodeIsASecondFactorOnce() throws Exception {
    Flow enrolment = start(ANNE, "enrol-recovery-codes", "");
    String code = enrolment.getResult().getIssued(0).unpack(RecoveryCodes.class).getCodes(0);

    assertThat(token(answer(answer(start("", "sign-in-with-two-factors", EMAIL), PasswordAnswer.newBuilder()
        .setPassword(PASSWORD).build()), RecoveryCodeAnswer.newBuilder().setCode(code).build()))).isNotEmpty();

    Flow again = answer(start("", "sign-in-with-two-factors", EMAIL), PasswordAnswer.newBuilder()
        .setPassword(PASSWORD).build());
    assertThat(status(() -> answer(again, RecoveryCodeAnswer.newBuilder().setCode(code).build())))
        .isEqualTo(GrpcStatus.PERMISSION_DENIED);
  }

  @Test
  void aCodeVerifiesAnIdentifier() throws Exception {
    Flow flow = start(ANNE, "verify", EMAIL);
    assertThat(flow.getDeliveries(0).getTo()).isEqualTo(EMAIL);

    Flow done = answer(flow, CodeAnswer.newBuilder().setCode(flow.getDeliveries(0).getCode()).build());

    assertThat(done.getResult().getResources(0).getFieldsMap().get("verified").getBoolValue()).isTrue();
  }

  @Test
  void aRecoverySetsANewPasswordAndEndsTheSessions() throws Exception {
    Flow verify = start(ANNE, "verify", EMAIL);
    answer(verify, CodeAnswer.newBuilder().setCode(verify.getDeliveries(0).getCode()).build());
    String token = token(answer(start("", "sign-in", EMAIL), PasswordAnswer.newBuilder().setPassword(PASSWORD)
        .build()));

    Flow recovery = start("", "recover", EMAIL);
    Flow chosen = answer(recovery, CodeAnswer.newBuilder().setCode(recovery.getDeliveries(0).getCode()).build());
    answer(chosen, PasswordAnswer.newBuilder().setPassword("a brand new passphrase").build());

    assertThat(status(() -> authenticate(token))).isEqualTo(GrpcStatus.UNAUTHENTICATED);
    assertThat(token(answer(start("", "sign-in", EMAIL), PasswordAnswer.newBuilder()
        .setPassword("a brand new passphrase").build()))).isNotEmpty();
  }

  @Test
  void aRetriedAnswerGivesTheFirstResult() throws Exception {
    Flow flow = start("", "sign-in", EMAIL);
    ContinueFlowRequest request = ContinueFlowRequest.newBuilder().setFlow(flow.getName())
        .setRevision(flow.getRevision()).setRequestId(UUID.randomUUID().toString())
        .setAnswer(Any.pack(PasswordAnswer.newBuilder().setPassword(PASSWORD).build())).build();

    Flow first = installation.authentication.continueFlow(request).await(10, TimeUnit.SECONDS);
    Flow retried = installation.authentication.continueFlow(request).await(10, TimeUnit.SECONDS);

    assertThat(retried).isEqualTo(first);
  }

  @Test
  void factorsComeOnlyFromFlows() {
    assertThat(status(() -> create("accounts/anne", TypeSystem.FACTOR, "", Map.of()))).isEqualTo(
        GrpcStatus.FAILED_PRECONDITION);
  }

  private Flow start(String subject, String definition, String email) throws Exception {
    StartFlowRequest.Builder request = StartFlowRequest.newBuilder().setSubject(subject).setDefinition(definition);
    if (!email.isEmpty()) {
      request.setIdentifierKind(IdentifierKind.EMAIL).setIdentifier(email);
    }
    return installation.authentication.startFlow(request.build()).await(10, TimeUnit.SECONDS);
  }

  private Flow answer(Flow flow, Message answer) throws Exception {
    return installation.authentication.continueFlow(ContinueFlowRequest.newBuilder().setFlow(flow.getName())
        .setRevision(flow.getRevision()).setRequestId(UUID.randomUUID().toString()).setAnswer(Any.pack(answer))
        .build()).await(10, TimeUnit.SECONDS);
  }

  private Principal authenticate(String token) throws Exception {
    return installation.authentication.authenticate(AuthenticateRequest.newBuilder().setSessionToken(token).build())
        .await(10, TimeUnit.SECONDS);
  }

  private String code(byte[] secret) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA1");
    mac.init(new SecretKeySpec(secret, "HmacSHA1"));
    byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES)
        .putLong(installation.clock.instant().getEpochSecond() / 30).array());
    int offset = hash[hash.length - 1] & 0xf;
    int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
        | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
    return String.format("%06d", binary % 1_000_000);
  }

  private Resource create(String parent, String type, String id, Map<String, Value> fields) throws Exception {
    return installation.data.createResource(CreateResourceRequest.newBuilder().setSubject(ANNE).setParent(parent)
        .setResourceId(id).setResource(Resource.newBuilder().setType(type).putAllFields(fields)).build())
        .await(10, TimeUnit.SECONDS);
  }

  private static String token(Flow done) throws Exception {
    return done.getResult().getIssued(0).unpack(SessionToken.class).getToken();
  }

  private static byte[] secret(String uri) {
    for (String parameter : URI.create(uri).getRawQuery().split("&")) {
      if (parameter.startsWith("secret=")) {
        return BaseEncoding.base32().decode(parameter.substring("secret=".length()));
      }
    }
    throw new AssertionError("The URI " + uri + " has no secret");
  }

  private static Value value(String text) {
    return Value.newBuilder().setStringValue(text).build();
  }

  private static GrpcStatus status(ThrowingCallable call) {
    GrpcStatus[] status = new GrpcStatus[1];
    assertThatThrownBy(call).isInstanceOfSatisfying(InvalidStatusException.class,
        failure -> status[0] = failure.actualStatus());
    return status[0];
  }
}
