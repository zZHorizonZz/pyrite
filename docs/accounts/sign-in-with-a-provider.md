# Sign in with a provider

Let people sign in, and sign up, with an account at a provider, such as Google, Microsoft, GitHub or Discord, and link
such accounts to the accounts that they have.

A provider sign-in is an OAuth 2.0 authorization-code sign-in with PKCE. Pyrite gives the authorization URL of each
provider, your application sends the person there and takes the callback, and Pyrite trades the code for the account
at the provider. A provider of OpenID Connect, such as Google or Microsoft, gives an ID token, which Pyrite checks
with the provider's keys and the nonce of the stage; any other provider, such as GitHub or Discord, names the account
in its user info. A provider sign-in counts as a factor of its own, so a stage of `factors: 2` takes it with a password
or a TOTP code.

A linked provider account is a factor of its account, of the kind `PROVIDER`. An account links as many as it wants,
and no provider account is linked to two accounts: the database refuses the second link.

## Before you begin

- Set up accounts and sessions. See [Sign in with a password](sign-in-with-a-password.md).
- Register your application at each provider, with a callback URL of your application for it, such as
  `https://acme.dev/callback/google`. Keep the client id and secret that the provider gives.
- Add `io.vertx:vertx-auth-oauth2`, which Pyrite depends on, for the providers of Vert.x.

## Give Pyrite the providers

1. Build each provider with Vert.x, and add it to the options of authentication with a name, its callback and its
   scopes:

   ```java
   OAuth2Auth google = GoogleAuth.discover(vertx, new OAuth2Options().setClientId(googleId)
       .setClientSecret(googleSecret)).await();
   OAuth2Auth github = GithubAuth.create(vertx, githubId, githubSecret);

   AuthenticationOptions authentication = new AuthenticationOptions()
       .addProvider(new ProviderOptions().setName("google").setAuth(google)
           .setRedirectUri("https://acme.dev/callback/google").addScope("openid").addScope("email"))
       .addProvider(new ProviderOptions().setName("github").setAuth(github)
           .setRedirectUri("https://acme.dev/callback/github").addScope("read:user"));
   ```

   Build an OpenID Connect provider with discovery, such as `GoogleAuth.discover` or `AzureADAuth.discover`, so that
   Vert.x has the keys that check its ID tokens, and ask for the scope `openid`. A provider without a Vert.x preset,
   such as Discord, is an `OAuth2Auth.create` with its authorization, token and user info paths. The client secrets
   stay in your application; the declaration never holds them.

2. Declare the exchanges. An exchange of the input `NONE` starts from nothing; the provider answer names the account.
   Choose what an unlinked provider account does per exchange:

   ```textproto
   exchanges { name: "sign-in-at-provider" input: NONE stages { factors: 1 } completes: ISSUE_SESSION }
   exchanges {
     name: "sign-up-at-provider"
     input: NONE
     stages { factors: 1 }
     completes: [CREATE_ACCOUNT, ISSUE_SESSION]
   }
   exchanges { name: "link-provider" input: SUBJECT stages { enrol: PROVIDER } completes: ACTIVATE_FACTOR }
   ```

   `sign-in-at-provider` signs in the accounts that linked the provider account, and refuses any other with
   `FAILED_PRECONDITION`. `sign-up-at-provider` also creates an account, with a random id, for a provider account
   that no account links, and links it. `link-provider` links a provider account to the account that calls.

## Match the email address of an existing account

A person who signed up with a password, and later signs in with Google at the same address, has an account already.
An exchange of the input `NONE` can find it by the email address, when its `email_match` says so:

```textproto
exchanges {
  name: "sign-in-at-provider"
  input: NONE
  stages { factors: 1 }
  completes: [CREATE_ACCOUNT, ISSUE_SESSION]
  email_match: CONFIRM
}
```

The email address matches only when the provider says that it verified it, with the claim `email_verified` of OpenID
Connect, and a verified identifier of an account
holds it. A provider account that an account links already never matches by email; it signs in to its account.

| `email_match` | A provider account of a matching email address                                                                                                                                               |
| ------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| unset         | Reaches no account: `CREATE_ACCOUNT` creates one, or the exchange refuses it                                                                                                                 |
| `SIGN_IN`     | Signs in to the account, and the exchange links the provider account to it when it ends                                                                                                      |
| `CONFIRM`     | Makes the exchange one of the account, without a factor: the stage asks for a factor of the account, such as its password, and the exchange links the provider account once the stage is met |

With `CONFIRM`, the response to the provider answer is not done. Its `provider_account` names the provider account
that the next answer links, so your page can say "Enter your password to link your Google account", and its
challenges are those of the account's factors. `SIGN_IN` trusts the provider with the account: take it only for
providers that verify addresses as you do.


1. Start the exchange, and send the person to the authorization URL of the provider that they chose:

   ```java
   Exchange signIn = authentication.startExchange(StartExchangeRequest.newBuilder()
       .setDefinition("sign-up-at-provider").build()).await();
   ProviderRedirects redirects = signIn.getChallengesList().stream()
       .filter(challenge -> challenge.getMethod().equals("pyrite.authentication.v1.ProviderAnswer")).findFirst()
       .orElseThrow().getParameters().unpack(ProviderRedirects.class);
   ```

   Each `ProviderRedirect` holds a provider's name and its `authorization_url`, with the state, the PKCE challenge
   and the nonce of the stage. Keep the name of the exchange, such as in a cookie, for the callback.

2. In the callback, answer the exchange with the code and the state that the provider sent:

   ```java
   Exchange done = authentication.continueExchange(ContinueExchangeRequest.newBuilder()
       .setExchange(signIn.getName()).setRevision(signIn.getRevision()).setRequestId(UUID.randomUUID().toString())
       .setAnswer(Any.pack(ProviderAnswer.newBuilder().setProvider("google").setCode(code).setState(state).build()))
       .build()).await();
   TokenSet tokens = done.getResult().getIssued(0).unpack(TokenSet.class);
   ```

   A state of another exchange, a code that the provider refuses or that was traded before, and an ID token with
   another nonce fail as a wrong answer, with `PERMISSION_DENIED`. The state and the nonce last as long as the stage's
   nonce, `codeLifetime`.

## Link a provider account

Start `link-provider` as the account, and answer it with a provider sign-in as above:

```java
Exchange link = authentication.startExchange(StartExchangeRequest.newBuilder().setSubject("user:anne")
    .setDefinition("link-provider").build()).await();
```

A provider account that an account links already fails with `ALREADY_EXISTS`.

## Unlink a provider account

List the factors of the account, and remove the one of the provider, as the account:

```java
Factor google = authentication.listFactors(ListFactorsRequest.newBuilder().setSubject("user:anne")
    .setAccount("accounts/anne").build()).await().getFactorsList().stream()
    .filter(factor -> factor.getProvider().equals("google")).findFirst().orElseThrow();
authentication.deleteFactor(DeleteFactorRequest.newBuilder().setSubject("user:anne").setName(google.getName())
    .build()).await();
```

The provider account then signs in to nothing, and any account can link it again. `DeleteFactor` takes any factor,
such as a lost passkey, but an account keeps a factor that signs in on its own: its last password, passkey or linked
provider account fails with `FAILED_PRECONDITION` until it has another.

## Verify

The first `sign-up-at-provider` of a provider account gives a result whose `account` is new, such as
`accounts/u3f2a...`, with a factor of the kind `PROVIDER`, and a `TokenSet`. Each later sign-in of the same provider
account gives the same account. `sign-in-at-provider` of a provider account that no account links fails with
`FAILED_PRECONDITION`.

## Related

- [About accounts and exchanges](about-accounts-and-exchanges.md)
- [Options](../deployment/options.md)
- [Declaration reference](../declarations/declaration-reference.md)
- [Troubleshooting accounts](troubleshooting-accounts.md)
