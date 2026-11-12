# Sign in with a password

Let people sign in with an email address and a password, and authenticate each later call with the session that the
sign-in issues.

## Before you begin

- Declare `pyrite.dev/Key`, and give Pyrite a root key. See [Serve the services](../deployment/serve-the-services.md).
- Have a resource that keys go under, such as `organizations/acme`, on which the subject of the steps below may
  create keys. See [Encrypt data](../keys/encrypt-data.md).
- The examples call the authentication service through a client of your gRPC server:

  ```java
  AuthenticationGrpcClient authentication = AuthenticationGrpcClient.create(client);
  ```

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
     permissions { grant: "admin" }
     grantable: ["admin", "viewer"]
   }
   ```

2. Declare the kind of identifier that people sign in with. `match` decides what an email address looks like, and
   `normalize` gives the value that Pyrite stores and compares:

   ```textproto
   identifier_kinds {
     name: "email"
     match: "value.trim().matches(r'^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$')"
     normalize: "value.trim().lowerAscii()"
     receives_codes: true
   }
   ```

   A flow that starts from an identifier needs at least one kind. See
   [Declaration reference](../declarations/declaration-reference.md).

3. Declare a flow that enrols a password, and one that signs in:

   ```textproto
   flows { name: "enrol-password" input: SUBJECT stages { enrol: PASSWORD } completes: ACTIVATE_FACTOR }
   flows { name: "sign-in" input: IDENTIFIER stages { factors: 1 } completes: ISSUE_SESSION }
   ```

4. Give Pyrite the options of authentication: the MAC key of session tokens and codes, the encryption key of factor
   secrets, the issuer that authenticator apps show, and the WebAuthn relying party:

   ```java
   PyriteOptions options = new PyriteOptions().setKeyOptions(new KeyOptions().setRoot(root))
       .setAuthenticationOptions(new AuthenticationOptions().setMacKey("keys/sessions")
           .setEncryptionKey("keys/factors").setIssuer("Acme")
           .setRelyingPartyOptions(new RelyingPartyOptions().setId("acme.dev").setName("Acme")
               .addOrigin("https://acme.dev")));
   ```

   `WriteDeclaration` refuses a declaration with accounts while the MAC key, the encryption key, the issuer, or the
   id or the name of the relying party is not set. See [Options](../deployment/options.md).

5. Make Pyrite with these options, and add its services to your server:

   ```java
   Pyrite.pyrite(vertx, pool, driver, openFga, options).bind(server);
   ```

6. Write the declaration, then create the two keys that the options name:

   ```java
   CryptographyGrpcClient cryptography = CryptographyGrpcClient.create(client);
   cryptography.createKey(CreateKeyRequest.newBuilder().setSubject("user:anne").setKeyId("sessions")
       .setParent("organizations/acme").setAlgorithm(Algorithm.HMAC_SHA256).build()).await();
   cryptography.createKey(CreateKeyRequest.newBuilder().setSubject("user:anne").setKeyId("factors")
       .setParent("organizations/acme").setAlgorithm(Algorithm.AES256_GCM).build()).await();
   ```

## Create an account

1. Create the account. Its id is the id of its principal, so `accounts/anne` is `user:anne`:

   ```java
   authentication.createAccount(CreateAccountRequest.newBuilder().setSubject("user:anne").setAccountId("anne")
       .setKind(Account.Kind.HUMAN).build()).await();
   ```

   `CreateAccount` checks no relation, so call it from your sign-up code. An id that an account has already fails
   with `ALREADY_EXISTS`.

2. Add the email address to the account. The subject needs `admin` on the account, which Anne has as its `owner`:

   ```java
   authentication.addIdentifier(AddIdentifierRequest.newBuilder().setSubject("user:anne")
       .setAccount("accounts/anne").setKind("email").setValue("Anne@Acme.dev").setPrimary(true).build()).await();
   ```

   Pyrite stores the address as the kind's `normalize` gives it, `anne@acme.dev`, unverified. An address that an
   account has already fails with `ALREADY_EXISTS`, and a value that is not an email address fails with
   `INVALID_ARGUMENT`.

## Enrol a password

1. Start the enrolment as the account:

   ```java
   Flow enrolment = authentication.startFlow(StartFlowRequest.newBuilder().setSubject("user:anne")
       .setDefinition("enrol-password").build()).await();
   ```

2. Answer its challenge with the new password:

   ```java
   Flow enrolled = authentication.continueFlow(ContinueFlowRequest.newBuilder().setFlow(enrolment.getName())
       .setRevision(enrolment.getRevision()).setRequestId(UUID.randomUUID().toString())
       .setAnswer(Any.pack(PasswordAnswer.newBuilder().setPassword("correct horse battery staple").build()))
       .build()).await();
   ```

   A password has 8 to 128 characters; another length fails with `INVALID_ARGUMENT`. The new password replaces the
   account's old one, if it had one.

## Sign in

1. Start the sign-in with the address, as the person typed it:

   ```java
   Flow signIn = authentication.startFlow(StartFlowRequest.newBuilder().setDefinition("sign-in")
       .setIdentifier("anne@acme.dev").build()).await();
   ```

   The request names no kind, so Pyrite takes the first declared kind whose `match` the address meets. To take one
   kind only, name it with `setIdentifierKind("email")`; a value of another kind then fails with `INVALID_ARGUMENT`.

2. Answer with the password:

   ```java
   Flow done = authentication.continueFlow(ContinueFlowRequest.newBuilder().setFlow(signIn.getName())
       .setRevision(signIn.getRevision()).setRequestId(UUID.randomUUID().toString())
       .setAnswer(Any.pack(PasswordAnswer.newBuilder().setPassword("correct horse battery staple").build()))
       .build()).await();
   ```

   A wrong password fails with `PERMISSION_DENIED`, and leaves the flow at its revision, so the person can try
   again. After `maxAnswers` wrong answers, 5 by default, the flow takes no more.

3. Take the session token from the result, and give it to the person, such as in a cookie:

   ```java
   String token = done.getResult().getIssued(0).unpack(SessionToken.class).getToken();
   ```

   Pyrite gives the token once, and keeps only a MAC of its secret.

## Authenticate a call

Exchange the token for the principal, and use its subject in the calls that follow:

```java
Principal principal = authentication.authenticate(AuthenticateRequest.newBuilder().setSessionToken(token)
    .build()).await();
System.out.println(principal.getSubject());
```

## Verify

`enrolled.getResult().getFactors(0)` is a factor of the kind `PASSWORD` of `accounts/anne`. The sign-in prints the
subject of the account:

```text
user:anne
```

`principal.getSession()` is the session that the sign-in issued, and `principal.getExpireTime()` its expiry, 30 days
after the sign-in by default. A token that is wrong, of a session that ended, or of one that went unused for its idle
lifetime fails with `UNAUTHENTICATED`. See [Troubleshooting accounts](troubleshooting-accounts.md).

## Related

- [About accounts and flows](about-accounts-and-flows.md)
- [Add a second factor](add-a-second-factor.md)
- [Add a passkey](add-a-passkey.md)
- [Options](../deployment/options.md)
