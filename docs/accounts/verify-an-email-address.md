# Verify an email address

Prove that a person receives mail at an address of their account, and mark the address as verified.

## Before you begin

- Set up accounts, and give the account an email address. See [Sign in with a password](sign-in-with-a-password.md).
- Declare the identifier kind `email` with `receives_codes: true`. Pyrite sends no code to an identifier of a kind
  without it. See [Declaration reference](../declarations/declaration-reference.md).
- Have a way to send mail from your application. Pyrite makes the code, and your application sends it.

## Verify the address

1. Declare the flow. Its input is the account and one of its identifiers, and its stage requires the code:

   ```textproto
   flows { name: "verify" input: SUBJECT_IDENTIFIER stages { identifier: true } completes: MARK_VERIFIED }
   ```

2. Start the flow as the account, with the address:

   ```java
   Flow flow = authentication.startFlow(StartFlowRequest.newBuilder().setSubject("user:anne")
       .setDefinition("verify").setIdentifierKind("email").setIdentifier("anne@acme.dev").build()).await();
   ```

   An address that is not the account's fails with `NOT_FOUND`, so an account verifies only its own addresses.

3. Take the delivery of the flow, and send its code to its address. Its `kind` is `email`, so send a mail:

   ```java
   Delivery delivery = flow.getDeliveries(0);
   mailer.send(delivery.getTo(), "Your code is " + delivery.getCode());
   ```

   The code has six digits. It expires at `delivery.getExpireTime()`, `codeLifetime` after Pyrite made it, 10
   minutes by default. `delivery.getIdentifier()` names the identifier, such as `identifiers/0b0d...`.

   To send codes from a code interceptor instead, so that no response carries one, see
   [Intercept flows](intercept-flows.md#send-codes-from-your-application).

4. Answer with the code that the person types:

   ```java
   Flow done = authentication.continueFlow(ContinueFlowRequest.newBuilder().setFlow(flow.getName())
       .setRevision(flow.getRevision()).setRequestId(UUID.randomUUID().toString())
       .setAnswer(Any.pack(CodeAnswer.newBuilder().setCode("381204").build())).build()).await();
   ```

   A wrong code, an expired one, or the code of another flow fails with `PERMISSION_DENIED`. After `maxAnswers`
   wrong codes, 5 by default, start another flow, which sends a new code.

Show the code to nobody but the owner of the address: not in the response to the caller that started the flow, and
not in a log.

## Verify

`done.getResult().getIdentifiers(0)` is the identifier, with `verified` set to `true`. `ListIdentifiers` gives it the
same way from then on, and a recovery sends codes to it.

## Related

- [About accounts and flows](about-accounts-and-flows.md)
- [Recover an account](recover-an-account.md)
- [Intercept flows](intercept-flows.md)
