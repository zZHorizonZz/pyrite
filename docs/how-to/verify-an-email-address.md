# Verify an email address

Prove that a person receives mail at an identifier of their account, and mark the identifier as verified. Pyrite
generates the code, and your application sends it.

## Before you begin

- Set up accounts, and give the account an email identifier. For more information, refer to
  [Sign in with a password](sign-in-with-a-password.md).
- Declare the identifier kind `email` with `receives_codes: true`. Pyrite sends no code to an identifier of a kind
  without it. For more information, refer to [Declaration](../reference/declaration.md#identifierkind).
- Have a way to send email from your application.

## Verify the address

1. Declare the flow. Its input is the account and the identifier, and its stage requires the code:

   ```textproto
   flows { name: "verify" input: SUBJECT_IDENTIFIER stages { identifier: true } completes: MARK_VERIFIED }
   ```

2. Start the flow as the account, with the address:

   ```java
   Flow flow = authentication.startFlow(StartFlowRequest.newBuilder().setSubject("user:anne")
       .setDefinition("verify").setIdentifierKind("email").setIdentifier("anne@acme.dev").build())
       .await();
   ```

3. Take the flow's delivery. Its `kind` is `email`, so send `delivery.getCode()` to `delivery.getTo()` with your
   mailer:

   ```java
   Delivery delivery = flow.getDeliveries(0);
   ```

   `delivery.getIdentifier()` names the identifier, such as `identifiers/0b0d...`. Pyrite does not mask the address.
   To show the person where the code went, format `delivery.getTo()` as you like.

   The code has six digits. It expires at `delivery.getExpireTime()`, `codeLifetime` after Pyrite generated it, 10
   minutes by default.

4. Answer with the code that the person types:

   ```java
   Flow done = authentication.continueFlow(ContinueFlowRequest.newBuilder().setFlow(flow.getName())
       .setRevision(flow.getRevision()).setRequestId(UUID.randomUUID().toString())
       .setAnswer(Any.pack(CodeAnswer.newBuilder().setCode("381204").build())).build()).await();
   ```

## Check the result

`done.getResult().getIdentifiers(0)` is the identifier, with `verified` set to `true`. `ListIdentifiers` returns it
the same way from then on.

Do not show the delivery's code to the caller that started the flow. Only the owner of the address may see it.

## Related resources

- [Accounts and flows](../concepts/accounts-and-flows.md)
- [Recover an account](recover-an-account.md)
