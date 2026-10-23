# Add a second factor

Require a second factor at sign-in: a TOTP code from an authenticator app, or a recovery code when the app is lost.
A password proves knowledge, and both codes prove possession, so the two together meet `factors: 2`.

## Before you begin

- Set up accounts and password sign-in. For more information, refer to
  [Sign in with a password](sign-in-with-a-password.md).

## Declare the flows

Add a flow that enrols a TOTP authenticator, one that issues recovery codes, and a sign-in that takes two factors:

```textproto
flows { name: "enrol-totp" input: SUBJECT stages { enrol: TOTP } completes: ACTIVATE_FACTOR }
flows { name: "enrol-recovery-codes" input: SUBJECT stages { enrol: RECOVERY_CODES } completes: ACTIVATE_FACTOR }
flows { name: "sign-in-with-two-factors" input: IDENTIFIER stages { factors: 2 } completes: ISSUE_SESSION }
```

## Enrol a TOTP authenticator

1. Start the enrolment as the account:

   ```java
   Flow enrolment = authentication.startFlow(StartFlowRequest.newBuilder().setSubject("user:anne")
       .setDefinition("enrol-totp").build()).await();
   ```

2. Show the `otpauth` URI to the person, as a QR code. It is in the challenge's parameters:

   ```java
   String uri = enrolment.getChallenges(0).getParameters().unpack(TotpProvisioning.class).getUri();
   ```

3. Answer with the first code that the app shows. The answer proves that the app holds the secret:

   ```java
   Flow enrolled = authentication.continueFlow(ContinueFlowRequest.newBuilder().setFlow(enrolment.getName())
       .setRevision(enrolment.getRevision()).setRequestId(UUID.randomUUID().toString())
       .setAnswer(Any.pack(TotpAnswer.newBuilder().setCode("492039").build())).build()).await();
   ```

   The factor in `enrolled.getResult()` has the state `ACTIVE`.

Pyrite's TOTP follows RFC 6238: SHA-1, six digits, a period of 30 seconds.

## Issue recovery codes

Start the flow. It needs no answer, so it is done at once, and its result holds ten codes:

```java
Flow codes = authentication.startFlow(StartFlowRequest.newBuilder().setSubject("user:anne")
    .setDefinition("enrol-recovery-codes").build()).await();
List<String> recovery = codes.getResult().getIssued(0).unpack(RecoveryCodes.class).getCodesList();
```

Show the codes to the person once. Pyrite keeps only their MACs. Each code works once.

## Sign in with two factors

1. Start the sign-in, and answer with the password, as for one factor. The flow is not done: its challenges now
   include `pyrite.authentication.v1.TotpAnswer` and `pyrite.authentication.v1.RecoveryCodeAnswer`.

2. Answer with a code from the app:

   ```java
   Flow done = authentication.continueFlow(ContinueFlowRequest.newBuilder().setFlow(flow.getName())
       .setRevision(flow.getRevision()).setRequestId(UUID.randomUUID().toString())
       .setAnswer(Any.pack(TotpAnswer.newBuilder().setCode("719306").build())).build()).await();
   ```

   Or answer with a recovery code instead:

   ```java
   Flow done = authentication.continueFlow(ContinueFlowRequest.newBuilder().setFlow(flow.getName())
       .setRevision(flow.getRevision()).setRequestId(UUID.randomUUID().toString())
       .setAnswer(Any.pack(RecoveryCodeAnswer.newBuilder().setCode(recovery.getFirst()).build())).build()).await();
   ```

3. Take the session token from `done.getResult()`.

## Related resources

- [Accounts and flows](../concepts/accounts-and-flows.md)
- [RFC 6238: TOTP](https://www.rfc-editor.org/rfc/rfc6238)
