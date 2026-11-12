# Add a second factor

Require a TOTP code from an authenticator app at sign-in, besides the password, and give out recovery codes for when
the app is lost.

## Before you begin

- Set up accounts and password sign-in. See [Sign in with a password](sign-in-with-a-password.md).
- Set the issuer in the options of authentication, such as `setIssuer("Acme")`. Authenticator apps show it next to
  the account.

## Declare the flows

Add a flow that enrols a TOTP authenticator, one that gives out recovery codes, and a sign-in that takes two
factors:

```textproto
flows { name: "enrol-totp" input: SUBJECT stages { enrol: TOTP } completes: ACTIVATE_FACTOR }
flows { name: "enrol-recovery-codes" input: SUBJECT stages { enrol: RECOVERY_CODES } completes: ACTIVATE_FACTOR }
flows { name: "sign-in-with-two-factors" input: IDENTIFIER stages { factors: 2 } completes: ISSUE_SESSION }
```

A password proves knowledge, and a TOTP code or a recovery code proves possession, so a password with either code
meets `factors: 2`. Two passwords do not.

## Enrol a TOTP authenticator

1. Start the enrolment as the account:

   ```java
   Flow enrolment = authentication.startFlow(StartFlowRequest.newBuilder().setSubject("user:anne")
       .setDefinition("enrol-totp").build()).await();
   ```

2. Show the `otpauth` URI of the challenge to the person, as a QR code:

   ```java
   String uri = enrolment.getChallenges(0).getParameters().unpack(TotpProvisioning.class).getUri();
   ```

   ```text
   otpauth://totp/Acme:accounts%2Fanne?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP&issuer=Acme&algorithm=SHA1&digits=6&period=30
   ```

   Each enrolment makes a secret of its own. The code follows RFC 6238: SHA-1, six digits, a step of 30 seconds.

3. Answer with the code that the app shows. The answer proves that the app holds the secret:

   ```java
   Flow enrolled = authentication.continueFlow(ContinueFlowRequest.newBuilder().setFlow(enrolment.getName())
       .setRevision(enrolment.getRevision()).setRequestId(UUID.randomUUID().toString())
       .setAnswer(Any.pack(TotpAnswer.newBuilder().setCode("492039").build())).build()).await();
   ```

   A wrong code fails with `PERMISSION_DENIED`, and activates nothing. The new secret replaces the account's old one,
   if it had one.

## Give out recovery codes

Start the flow. It needs no answer, so it is done at once, and its result holds ten codes:

```java
Flow codes = authentication.startFlow(StartFlowRequest.newBuilder().setSubject("user:anne")
    .setDefinition("enrol-recovery-codes").build()).await();
List<String> recovery = codes.getResult().getIssued(0).unpack(RecoveryCodes.class).getCodesList();
```

Show the codes to the person once, and ask them to keep them. Pyrite keeps only their MACs. Each code works once, and
new codes replace the old ones.

## Sign in with two factors

1. Start the sign-in, and answer with the password, as for one factor. The flow is not done: its challenges now
   take `pyrite.authentication.v1.TotpAnswer`, `pyrite.authentication.v1.RecoveryCodeAnswer` and
   `pyrite.authentication.v1.PasskeyAnswer`.

   ```java
   Flow signIn = authentication.startFlow(StartFlowRequest.newBuilder().setDefinition("sign-in-with-two-factors")
       .setIdentifier("anne@acme.dev").build()).await();
   Flow halfway = authentication.continueFlow(ContinueFlowRequest.newBuilder().setFlow(signIn.getName())
       .setRevision(signIn.getRevision()).setRequestId(UUID.randomUUID().toString())
       .setAnswer(Any.pack(PasswordAnswer.newBuilder().setPassword("correct horse battery staple").build()))
       .build()).await();
   ```

2. Answer with a code from the app:

   ```java
   Flow done = authentication.continueFlow(ContinueFlowRequest.newBuilder().setFlow(halfway.getName())
       .setRevision(halfway.getRevision()).setRequestId(UUID.randomUUID().toString())
       .setAnswer(Any.pack(TotpAnswer.newBuilder().setCode("719306").build())).build()).await();
   ```

   The app's code of the step before or after the current one works too, for clocks that differ. Each code works
   once: the code that enrolled the app does not sign in, and neither does a code older than the last one taken.

   Or answer with a recovery code instead:

   ```java
   Flow done = authentication.continueFlow(ContinueFlowRequest.newBuilder().setFlow(halfway.getName())
       .setRevision(halfway.getRevision()).setRequestId(UUID.randomUUID().toString())
       .setAnswer(Any.pack(RecoveryCodeAnswer.newBuilder().setCode(recovery.getFirst()).build())).build()).await();
   ```

3. Take the session token from `done.getResult().getIssued(0)`, a `SessionToken`, as for one factor.

## Verify

`enrolled.getResult().getFactors(0)` is a factor of the kind `TOTP`, and `codes.getResult().getFactors(0)` one of the
kind `RECOVERY_CODES`. The sign-in issues a session only after the second answer: `halfway.getResult()` is empty, and
`done.getResult().getSessionsCount()` is 1. A recovery code that was used once fails with `PERMISSION_DENIED` the
next time.

## Related

- [About accounts and flows](about-accounts-and-flows.md)
- [Add a passkey](add-a-passkey.md)
- [RFC 6238: TOTP](https://www.rfc-editor.org/rfc/rfc6238)
