package dev.horizon.pyrite.conformance.factor;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerTotpText;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;

@Summary("A code that is not six digits is wrong.")
final class TotpCodeSyntax extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    enrolTotp();
    for (String code : List.of("", "12345", "1234567", "abcdef", " 123456")) {
      passPasswordStage();
      callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerTotpText(code)),
          Requirement.TOTP_WRONG);
    }
  }
}
