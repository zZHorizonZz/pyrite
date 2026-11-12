# Add a passkey

Let people enrol a passkey of their device, and sign in with it, in a stage that takes nothing else.

A passkey is a WebAuthn credential that the browser makes and keeps on an authenticator, such as a phone or a security
key. It proves possession, and inherence too when the authenticator verifies the person, such as by a fingerprint. It
is the one factor that a phishing-resistant stage takes, because each answer holds the origin of the page, which the
browser writes and the authenticator signs. Pyrite gives the options that the browser takes, and checks the answers
with webauthn4j.

## Before you begin

- Set up accounts and password sign-in. See [Sign in with a password](sign-in-with-a-password.md).
- Set the relying party in the options of authentication: its `id` is the domain of your site, its `name` is what
  the browser shows, and its origins are those of the pages that make and use passkeys. See
  [Options](../deployment/options.md).

  ```java
  new RelyingPartyOptions().setId("acme.dev").setName("Acme").addOrigin("https://acme.dev")
  ```

- Serve the pages over HTTPS, and use a browser with the WebAuthn JSON methods:
  `PublicKeyCredential.parseCreationOptionsFromJSON`, `PublicKeyCredential.parseRequestOptionsFromJSON` and
  `toJSON()` of a credential.

## Declare the flows

Add a flow that enrols a passkey, and a sign-in whose stage is phishing-resistant:

```textproto
flows { name: "enrol-passkey" input: SUBJECT stages { enrol: PASSKEY } completes: ACTIVATE_FACTOR }
flows {
  name: "sign-in-with-passkey"
  input: IDENTIFIER
  stages { factors: 1 phishing_resistant: true }
  completes: ISSUE_SESSION
}
```

A stage with `phishing_resistant` takes a passkey, and no password or code. A stage of `factors` without it takes a
passkey too, among the other answers: the `sign-in` of [Sign in with a password](sign-in-with-a-password.md) takes
one in place of the password.

## Enrol a passkey

1. Start the enrolment as the account, and take the creation options from its challenge:

   ```java
   Flow enrolment = authentication.startFlow(StartFlowRequest.newBuilder().setSubject("user:anne")
       .setDefinition("enrol-passkey").build()).await();
   String options = enrolment.getChallenges(0).getParameters().unpack(PasskeyOptions.class).getOptions();
   ```

   `options` is the JSON of the creation options, with the challenge of this flow:

   ```json
   {
     "challenge": "q8C3...",
     "rp": {"id": "acme.dev", "name": "Acme"},
     "user": {"id": "YWNjb3VudHMvYW5uZQ", "name": "accounts/anne", "displayName": "accounts/anne"},
     "pubKeyCredParams": [{"type": "public-key", "alg": -7}, {"type": "public-key", "alg": -8},
       {"type": "public-key", "alg": -257}],
     "timeout": 300000,
     "excludeCredentials": [],
     "authenticatorSelection": {"residentKey": "preferred", "userVerification": "preferred"},
     "attestation": "none"
   }
   ```

   `excludeCredentials` lists the passkeys that the account has, so that an authenticator does not enrol twice.

2. Send `options` to the page, and make the passkey in the browser. Send the JSON of the credential back to your
   server:

   ```js
   const publicKey = PublicKeyCredential.parseCreationOptionsFromJSON(JSON.parse(options));
   const credential = await navigator.credentials.create({ publicKey });
   const answer = JSON.stringify(credential.toJSON());
   ```

3. Answer the flow with it:

   ```java
   Flow enrolled = authentication.continueFlow(ContinueFlowRequest.newBuilder().setFlow(enrolment.getName())
       .setRevision(enrolment.getRevision()).setRequestId(UUID.randomUUID().toString())
       .setAnswer(Any.pack(PasskeyAnswer.newBuilder().setCredential(answer).build())).build()).await();
   ```

   The flow activates the passkey. An account has as many passkeys as it enrols, and a new one replaces none.

## Sign in with a passkey

1. Start the sign-in with the address, and take the request options from the challenge of
   `pyrite.authentication.v1.PasskeyAnswer`:

   ```java
   Flow signIn = authentication.startFlow(StartFlowRequest.newBuilder().setDefinition("sign-in-with-passkey")
       .setIdentifier("anne@acme.dev").build()).await();
   String options = signIn.getChallengesList().stream()
       .filter(challenge -> challenge.getMethod().equals("pyrite.authentication.v1.PasskeyAnswer"))
       .findFirst().orElseThrow().getParameters().unpack(PasskeyOptions.class).getOptions();
   ```

   ```json
   {"challenge": "Zx7Q...", "rpId": "acme.dev", "timeout": 300000, "allowCredentials": [], "userVerification": "preferred"}
   ```

   The options list no passkeys, so the browser offers those that the authenticator keeps for `acme.dev`. A passkey
   signs in only if the authenticator keeps it so, as a discoverable credential, which the creation options prefer.

2. Sign in with the passkey in the browser, and send the JSON of the credential back:

   ```js
   const publicKey = PublicKeyCredential.parseRequestOptionsFromJSON(JSON.parse(options));
   const credential = await navigator.credentials.get({ publicKey });
   const answer = JSON.stringify(credential.toJSON());
   ```

3. Answer the flow with it, and take the session token from the result:

   ```java
   Flow done = authentication.continueFlow(ContinueFlowRequest.newBuilder().setFlow(signIn.getName())
       .setRevision(signIn.getRevision()).setRequestId(UUID.randomUUID().toString())
       .setAnswer(Any.pack(PasskeyAnswer.newBuilder().setCredential(answer).build())).build()).await();
   String token = done.getResult().getIssued(0).unpack(SessionToken.class).getToken();
   ```

## What Pyrite refuses

Each refusal of a credential is a wrong answer: it fails with `PERMISSION_DENIED`, and counts against the flow.

| Answer                                                                 | Result              |
| ---------------------------------------------------------------------- | ------------------- |
| A credential from an origin that the relying party does not name       | `PERMISSION_DENIED` |
| A credential of another relying party id                               | `PERMISSION_DENIED` |
| A credential made with the options of another flow                     | `PERMISSION_DENIED` |
| A passkey of another account, or of none, as for an unknown address    | `PERMISSION_DENIED` |
| Text that is not the JSON of a WebAuthn credential of the right kind   | `PERMISSION_DENIED` |
| A `PasskeyAnswer` whose bytes do not parse                             | `INVALID_ARGUMENT`  |
| A `PasswordAnswer` or another answer, at a phishing-resistant stage    | `INVALID_ARGUMENT`  |

The browser refuses, before Pyrite sees anything, to make a passkey on an authenticator that holds one of
`excludeCredentials`, and to make or use a passkey on a page whose origin is not of the relying party id.

## Verify

`enrolled.getResult().getFactors(0)` is a factor of the kind `PASSKEY` of `accounts/anne`. The sign-in is done after
the one answer, and `done.getResult().getAccount()` is `accounts/anne`. A second enrolment gives creation options
whose `excludeCredentials` holds the id of the first passkey.

## Related

- [About accounts and flows](about-accounts-and-flows.md)
- [Add a second factor](add-a-second-factor.md)
- [Troubleshooting accounts](troubleshooting-accounts.md)
- [Web Authentication](https://www.w3.org/TR/webauthn-3/)
