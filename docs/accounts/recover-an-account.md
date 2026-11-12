# Recover an account

Let a person who forgot their password set a new one, after they prove that they receive mail at their address, and
end every session of the account in case someone else holds one.

## Before you begin

- Set up accounts and password sign-in. See [Sign in with a password](sign-in-with-a-password.md).
- Verify the address. Pyrite sends a recovery code only to a verified address. See
  [Verify an email address](verify-an-email-address.md).
- Have a way to send mail from your application.

## Recover the account

1. Declare the flow. It proves the address, enrols a new password, activates it, and ends the sessions:

   ```textproto
   flows {
     name: "recover"
     input: IDENTIFIER
     stages { identifier: true }
     stages { enrol: PASSWORD }
     completes: [ACTIVATE_FACTOR, END_SESSIONS]
   }
   ```

   The stage of the code comes first: an enrolment needs a proven account, and the code proves it.

2. Start the flow with the address. It takes no subject, because the person cannot sign in:

   ```java
   Flow flow = authentication.startFlow(StartFlowRequest.newBuilder().setDefinition("recover")
       .setIdentifierKind("email").setIdentifier("anne@acme.dev").build()).await();
   ```

3. Send each delivery of the flow to its `to`, by its `kind`, as in
   [Verify an email address](verify-an-email-address.md).

4. Answer with the code that the person types. The flow moves on to the stage of the enrolment:

   ```java
   Flow proven = authentication.continueFlow(ContinueFlowRequest.newBuilder().setFlow(flow.getName())
       .setRevision(flow.getRevision()).setRequestId(UUID.randomUUID().toString())
       .setAnswer(Any.pack(CodeAnswer.newBuilder().setCode("381204").build())).build()).await();
   ```

   Its challenge now takes a `pyrite.authentication.v1.PasswordAnswer`: the new password.

5. Answer with the new password:

   ```java
   Flow done = authentication.continueFlow(ContinueFlowRequest.newBuilder().setFlow(proven.getName())
       .setRevision(proven.getRevision()).setRequestId(UUID.randomUUID().toString())
       .setAnswer(Any.pack(PasswordAnswer.newBuilder().setPassword("a brand new passphrase").build())).build())
       .await();
   ```

A flow of an address that no account has, or that is not verified, does not fail. It has no deliveries, its
challenge has no `CodeSent` parameters, and no code is ever right. Show the person the same page either way, so that
the page does not tell whether the address has an account.

## Verify

`done.getResult().getFactors(0)` is the new factor of the kind `PASSWORD`. The new password signs in, and the old one
fails with `PERMISSION_DENIED`. `Authenticate` with a token issued before the recovery fails with `UNAUTHENTICATED`.

## Related

- [About accounts and flows](about-accounts-and-flows.md)
- [Verify an email address](verify-an-email-address.md)
- [Intercept flows](intercept-flows.md)
