# Recover an account

Let a person who forgot their password set a new one, after they prove that they receive mail at their address. The
recovery also ends every session of the account, in case someone else holds one.

## Before you begin

- Set up accounts and password sign-in. For more information, refer to
  [Sign in with a password](sign-in-with-a-password.md).
- Verify the address. Pyrite sends a recovery code only to a verified address. For more information, refer to
  [Verify an email address](verify-an-email-address.md).
- Have a way to send email from your application.

## Recover the account

1. Declare the flow. It proves the address, enrols a new password, activates it and ends the sessions:

   ```textproto
   flows {
     name: "recover"
     input: IDENTIFIER
     stages { identifier: true }
     stages { enrol: PASSWORD }
     completes: [ACTIVATE_FACTOR, END_SESSIONS]
   }
   ```

2. Start the flow with the address. It takes no subject, because the person cannot sign in:

   ```java
   Flow flow = authentication.startFlow(StartFlowRequest.newBuilder().setDefinition("recover")
       .setIdentifierKind(IdentifierKind.EMAIL).setIdentifier("anne@acme.dev").build()).await();
   ```

3. Send each of the flow's deliveries to its address, as in
   [Verify an email address](verify-an-email-address.md).

4. Answer with the code that the person types. The flow moves to the enrolment stage:

   ```java
   Flow chosen = authentication.continueFlow(ContinueFlowRequest.newBuilder().setFlow(flow.getName())
       .setRevision(flow.getRevision()).setRequestId(UUID.randomUUID().toString())
       .setAnswer(Any.pack(CodeAnswer.newBuilder().setCode("381204").build())).build()).await();
   ```

5. Answer with the new password:

   ```java
   authentication.continueFlow(ContinueFlowRequest.newBuilder().setFlow(chosen.getName())
       .setRevision(chosen.getRevision()).setRequestId(UUID.randomUUID().toString())
       .setAnswer(Any.pack(PasswordAnswer.newBuilder().setPassword("a brand new passphrase").build())).build())
       .await();
   ```

## Check the result

The new password signs in. `Authenticate` with a token issued before the recovery fails with `UNAUTHENTICATED`.

A flow for an address that no account has does not fail. It has no deliveries, its challenge has no `CodeSent`
parameters, and no code is ever right. Show the person the same page either way, so that the page does not tell
whether the address has an account.

## Related resources

- [Accounts and flows](../concepts/accounts-and-flows.md)
- [Verify an email address](verify-an-email-address.md)
