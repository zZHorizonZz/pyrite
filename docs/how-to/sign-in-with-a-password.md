# Sign in with a password

Let people sign in with an email address and a password, and authenticate each later call with the session that the
sign-in issues. This page sets up accounts, enrols a password, signs in and authenticates.

## Before you begin

- Declare `pyrite.dev/Key`, and give the services a root key. For more information, refer to
  [Encrypt data](encrypt-data.md#before-you-begin).
- Have an organization, or another parent of keys, such as `organizations/acme`.

## Set up accounts

1. Declare `pyrite.dev/Account`. Its relation `owner` takes a user directly, because Pyrite grants each account
   `owner` on itself:

   ```textproto
   types {
     name: "pyrite.dev/Account"
     definition {
       type: "user"
       relations { key: "owner" value { this {} } }
       relations {
         key: "admin"
         value { union { child { this {} } child { computed_userset { relation: "owner" } } } }
       }
       relations {
         key: "viewer"
         value { union { child { this {} } child { computed_userset { relation: "admin" } } } }
       }
       metadata {
         relations { key: "owner" value { directly_related_user_types { type: "user" } } }
         relations { key: "admin" value { directly_related_user_types { type: "user" } } }
         relations { key: "viewer" value { directly_related_user_types { type: "user" } } }
       }
     }
     permissions { read: "viewer" update: "admin" delete: "admin" grant: "admin" }
     grantable: ["admin", "viewer"]
     retention { seconds: 2592000 }
   }
   ```

2. Declare the two flows, one that enrols a password and one that signs in with it:

   ```textproto
   flows { name: "enrol-password" input: SUBJECT stages { enrol: PASSWORD } completes: ACTIVATE_FACTOR }
   flows { name: "sign-in" input: IDENTIFIER stages { factors: 1 } completes: ISSUE_SESSION }
   ```

3. Give the services the options of authentication: the MAC key of session tokens, the encryption key of factor
   secrets, the WebAuthn relying party, and the issuer name that authenticator apps show:

   ```java
   PyriteServiceOptions options = new PyriteServiceOptions().withRoot(root)
       .withAuthentication(new PyriteServiceOptions.Authentication("keys/sessions", "keys/factors",
           new PyriteServiceOptions.RelyingParty("acme.dev", "Acme", List.of("https://acme.dev")), "Acme"));
   ```

4. Add the authentication service, with the same options as the others:

   ```java
   server.addService(AuthenticationService.create(vertx, pool, driver, openFga, options));
   ```

5. Write the declaration, then create the two keys that the options name:

   ```java
   data.createResource(CreateResourceRequest.newBuilder().setSubject("user:anne").setParent("organizations/acme")
       .setResourceId("sessions").setResource(Resource.newBuilder().setType("pyrite.dev/Key")
           .putFields("purpose", Value.newBuilder().setStringValue("MAC").build())
           .putFields("algorithm", Value.newBuilder().setStringValue("HMAC_SHA256").build()))
       .build()).await();
   data.createResource(CreateResourceRequest.newBuilder().setSubject("user:anne").setParent("organizations/acme")
       .setResourceId("factors").setResource(Resource.newBuilder().setType("pyrite.dev/Key")
           .putFields("purpose", Value.newBuilder().setStringValue("ENCRYPTION").build())
           .putFields("algorithm", Value.newBuilder().setStringValue("AES256_GCM").build()))
       .build()).await();
   ```

## Create an account

1. Create the account. Its id is the id of its principal, so `accounts/anne` is `user:anne`:

   ```java
   data.createResource(CreateResourceRequest.newBuilder().setSubject("user:anne").setResourceId("anne")
       .setResource(Resource.newBuilder().setType("pyrite.dev/Account")
           .putFields("kind", Value.newBuilder().setStringValue("HUMAN").build()))
       .build()).await();
   ```

2. Add the email address as an identifier of the account:

   ```java
   data.createResource(CreateResourceRequest.newBuilder().setSubject("user:anne").setParent("accounts/anne")
       .setResourceId("work").setResource(Resource.newBuilder().setType("pyrite.dev/Identifier")
           .putFields("kind", Value.newBuilder().setStringValue("EMAIL").build())
           .putFields("value", Value.newBuilder().setStringValue("anne@acme.dev").build()))
       .build()).await();
   ```

## Enrol a password

1. Start the enrolment flow as the account:

   ```java
   Flow enrolment = authentication.startFlow(StartFlowRequest.newBuilder().setSubject("user:anne")
       .setDefinition("enrol-password").build()).await();
   ```

2. Answer its challenge with the new password:

   ```java
   authentication.continueFlow(ContinueFlowRequest.newBuilder().setFlow(enrolment.getName())
       .setRevision(enrolment.getRevision()).setRequestId(UUID.randomUUID().toString())
       .setAnswer(Any.pack(PasswordAnswer.newBuilder().setPassword("correct horse battery staple").build()))
       .build()).await();
   ```

A password has 8 to 128 characters.

## Sign in

1. Start the sign-in flow with the email address:

   ```java
   Flow signIn = authentication.startFlow(StartFlowRequest.newBuilder().setDefinition("sign-in")
       .setIdentifierKind(IdentifierKind.EMAIL).setIdentifier("anne@acme.dev").build()).await();
   ```

2. Answer with the password:

   ```java
   Flow done = authentication.continueFlow(ContinueFlowRequest.newBuilder().setFlow(signIn.getName())
       .setRevision(signIn.getRevision()).setRequestId(UUID.randomUUID().toString())
       .setAnswer(Any.pack(PasswordAnswer.newBuilder().setPassword("correct horse battery staple").build()))
       .build()).await();
   ```

3. Take the session token from the result, and give it to the person, such as in a cookie:

   ```java
   String token = done.getResult().getIssued(0).unpack(SessionToken.class).getToken();
   ```

   Pyrite returns the token once. It stores only a MAC of it.

## Authenticate a call

Exchange the token for the principal, and use its subject in the calls that follow:

```java
Principal principal = authentication.authenticate(AuthenticateRequest.newBuilder().setSessionToken(token)
    .build()).await();
System.out.println(principal.getSubject());
```

```txt
user:anne
```

A token that is wrong, expired or idle too long fails with `UNAUTHENTICATED`.

## Related resources

- [Accounts and flows](../concepts/accounts-and-flows.md)
- [Add a second factor](add-a-second-factor.md)
- [Options](../reference/options.md)
