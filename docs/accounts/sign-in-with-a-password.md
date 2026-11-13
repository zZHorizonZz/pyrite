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

   An exchange that starts from an identifier needs at least one kind. See
   [Declaration reference](../declarations/declaration-reference.md).

3. Declare an exchange that enrols a password, and one that signs in:

   ```textproto
   exchanges { name: "enrol-password" input: SUBJECT stages { enrol: PASSWORD } completes: ACTIVATE_FACTOR }
   exchanges { name: "sign-in" input: IDENTIFIER stages { factors: 1 } completes: ISSUE_SESSION }
   ```

4. Give Pyrite the options of authentication: the MAC key of refresh tokens and codes, the encryption key of factor
   secrets, the key that signs access tokens, the issuer, and the WebAuthn relying party:

   ```java
   PyriteOptions options = new PyriteOptions().setKeyOptions(new KeyOptions().setRoot(root))
       .setAuthenticationOptions(new AuthenticationOptions().setMacKey("keys/sessions")
           .setEncryptionKey("keys/factors").setAccessTokenKey("keys/access").setIssuer("Acme")
           .setRelyingPartyOptions(new RelyingPartyOptions().setId("acme.dev").setName("Acme")
               .addOrigin("https://acme.dev")));
   ```

   `WriteDeclaration` refuses a declaration with accounts while one of the three keys, the issuer, or the id, the name
   or an origin of the relying party is not set. See [Options](../deployment/options.md).

5. Make Pyrite with these options, and add its services to your server:

   ```java
   Pyrite.pyrite(vertx, pool, driver, openFga, options).bind(server);
   ```

6. Write the declaration, then create the three keys that the options name:

   ```java
   CryptographyGrpcClient cryptography = CryptographyGrpcClient.create(client);
   cryptography.createKey(CreateKeyRequest.newBuilder().setSubject("user:anne").setKeyId("sessions")
       .setParent("organizations/acme").setAlgorithm(Algorithm.HMAC_SHA256).build()).await();
   cryptography.createKey(CreateKeyRequest.newBuilder().setSubject("user:anne").setKeyId("factors")
       .setParent("organizations/acme").setAlgorithm(Algorithm.AES256_GCM).build()).await();
   cryptography.createKey(CreateKeyRequest.newBuilder().setSubject("user:anne").setKeyId("access")
       .setParent("organizations/acme").setAlgorithm(Algorithm.ES256).build()).await();
   ```

   Give the access token key no other use: a JWT that it signs for another purpose would pass as an access token.

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

   Pyrite stores the address as the kind's `normalize` gives it, `anne@acme.dev`, unverified. Another account may add
   the same address; only verifying it makes it one account's, and a sign-in with it reaches the account that verified
   it, or else the one that added it first. An address that this account has already fails with `ALREADY_EXISTS`, and a
   value that is not an email address fails with `INVALID_ARGUMENT`.

## Enrol a password

1. Start the enrolment as the account:

   ```java
   Exchange enrolment = authentication.startExchange(StartExchangeRequest.newBuilder().setSubject("user:anne")
       .setDefinition("enrol-password").build()).await();
   ```

2. Answer its challenge with the new password:

   ```java
   Exchange enrolled = authentication.continueExchange(ContinueExchangeRequest.newBuilder()
       .setExchange(enrolment.getName())
       .setRevision(enrolment.getRevision()).setRequestId(UUID.randomUUID().toString())
       .setAnswer(Any.pack(PasswordAnswer.newBuilder().setPassword("correct horse battery staple").build()))
       .build()).await();
   ```

   A password has 8 to 128 characters; another length fails with `INVALID_ARGUMENT`. The new password replaces the
   account's old one, if it had one.

## Sign in

1. Start the sign-in with the address, as the person typed it:

   ```java
   Exchange signIn = authentication.startExchange(StartExchangeRequest.newBuilder().setDefinition("sign-in")
       .setIdentifier("anne@acme.dev").build()).await();
   ```

   The request names no kind, so Pyrite takes the first declared kind whose `match` the address meets. To take one
   kind only, name it with `setIdentifierKind("email")`; a value of another kind then fails with `INVALID_ARGUMENT`.

2. Answer with the password:

   ```java
   Exchange done = authentication.continueExchange(ContinueExchangeRequest.newBuilder().setExchange(signIn.getName())
       .setRevision(signIn.getRevision()).setRequestId(UUID.randomUUID().toString())
       .setAnswer(Any.pack(PasswordAnswer.newBuilder().setPassword("correct horse battery staple").build()))
       .build()).await();
   ```

   A wrong password fails with `PERMISSION_DENIED`, and leaves the exchange at its revision, so the person can try
   again. After `maxAnswers` wrong answers, 5 by default, the exchange takes no more.

3. Take the tokens of the session from the result, and give them to the client, such as in cookies:

   ```java
   TokenSet tokens = done.getResult().getIssued(0).unpack(TokenSet.class);
   ```

   Pyrite gives the tokens once, and keeps only a MAC of the secret of the refresh token.

## Authenticate a call

Trade the access token for the principal, and use its subject in the calls that follow:

```java
Principal principal = authentication.authenticate(AuthenticateRequest.newBuilder()
    .setAccessToken(tokens.getAccessToken()).build()).await();
System.out.println(principal.getSubject());
```

A service that should not call Pyrite on each request verifies the access token on its own instead, as a JWT of type
`at+jwt` with the public key of `keys/access`, the issuer of the options, and its `exp`. Its `sub` is the subject.

## Refresh the tokens

An access token lasts 5 minutes. Before it expires, trade the refresh token for new tokens, and keep both:

```java
tokens = authentication.refreshSession(RefreshSessionRequest.newBuilder()
    .setRefreshToken(tokens.getRefreshToken()).build()).await();
```

Each refresh token works once. Keep only the newest one: a refresh token that comes back after its trade ends the
session. Each refresh also extends the idle lifetime of the session.

## Verify

`enrolled.getResult().getFactors(0)` is a factor of the kind `PASSWORD` of `accounts/anne`. The sign-in prints the
subject of the account:

```text
user:anne
```

`principal.getSession()` is the session that the sign-in issued, and `principal.getExpireTime()` its expiry, 30 days
after the sign-in by default. A refresh gives a new access token and a new refresh token of the same session. A token
that is wrong or expired, of a session that ended, or of one that went unrefreshed for its idle lifetime fails with
`UNAUTHENTICATED`. See [Troubleshooting accounts](troubleshooting-accounts.md).

## Related

- [About accounts and exchanges](about-accounts-and-exchanges.md)
- [Add a second factor](add-a-second-factor.md)
- [Add a passkey](add-a-passkey.md)
- [Options](../deployment/options.md)
