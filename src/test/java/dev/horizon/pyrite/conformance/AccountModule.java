package dev.horizon.pyrite.conformance;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.AnswerTotp;
import dev.horizon.pyrite.conformance.condition.CreateAccount;
import dev.horizon.pyrite.conformance.condition.KeepTotpSecret;
import dev.horizon.pyrite.conformance.condition.PrepareAccounts;
import dev.horizon.pyrite.conformance.condition.StartExchange;

/**
 * A scenario of accounts, which starts with anne's account: her email address, unverified, and her password.
 */
public abstract class AccountModule extends ConformanceModule {

  public static final String EMAIL = "anne@acme.dev";
  public static final String PASSWORD = "correct horse battery staple";

  /**
   * Prepares the keys that accounts need, and anne's account.
   */
  protected final void prepareAnne() {
    call(new PrepareAccounts());
    call(new CreateAccount("anne", EMAIL, PASSWORD));
  }

  /**
   * Starts a sign-in with two factors, and answers its first with anne's password.
   */
  protected final void passPasswordStage() {
    call(new StartExchange("", "sign-in-with-two-factors", EMAIL));
    call(new AnswerPassword(PASSWORD));
  }

  /**
   * Enrols a TOTP authenticator for anne, with the code of the step before the current one, so that a code of the
   * current step is still free.
   */
  protected final void enrolTotp() {
    call(new StartExchange(ANNE, "enrol-totp", ""));
    call(new KeepTotpSecret(Value.TOTP_SECRET));
    call(new AnswerTotp(Value.TOTP_SECRET, -1));
  }
}
