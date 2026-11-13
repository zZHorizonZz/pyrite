# Sign and verify tokens

Sign claims as a JWT, such as an access token that your gateway issues, verify it later, and publish the public key
so that other parties verify it without Pyrite.

## Before you begin

- Declare `pyrite.dev/Key`, and give Pyrite a root key. See [Encrypt data](encrypt-data.md#before-you-begin).
- Make the parent of the key known, such as `organizations/acme`.
- Have a client of the cryptography service, `CryptographyGrpcClient.create(client)`. See
  [Serve the services](../deployment/serve-the-services.md).

## Sign a JWT

1. Create a signing key. `ES256` signs with ECDSA on P-256, and gives the key the purpose `SIGNING`:

   ```java
   cryptography.createKey(CreateKeyRequest.newBuilder().setSubject("user:anne").setKeyId("oauth")
       .setParent("organizations/acme").setAlgorithm(Algorithm.ES256).build()).await();
   ```

2. Sign the claims, with the `typ` of the token. Include `exp`, because a verification rejects a token without it:

   ```java
   long now = Instant.now().getEpochSecond();
   Struct claims = Struct.newBuilder()
       .putFields("iss", Value.newBuilder().setStringValue("https://auth.acme.dev").build())
       .putFields("aud", Value.newBuilder().setStringValue("https://api.acme.dev").build())
       .putFields("sub", Value.newBuilder().setStringValue("user:anne").build())
       .putFields("exp", Value.newBuilder().setNumberValue(now + 600).build()).build();
   SignResponse signed = cryptography.sign(SignRequest.newBuilder().setSubject("user:anne")
       .setKey("keys/oauth").setClaims(claims).setType("at+jwt").build()).await();
   String token = signed.getToken();
   ```

   `Value` is `com.google.protobuf.Value`. A whole number, such as `exp`, goes into the token as an integer. Without
   a type, the `typ` is `JWT`. The subject needs `signer` on the key. The header's `alg` is `ES256`, and its `kid` is
   the id of the version that signed, such as `oauth-1`.

## Verify a JWT

1. Name what the token must be: its `typ`, its issuer, an audience, and the leeway for the clocks of other servers:

   ```java
   Expected expected = Expected.newBuilder().setType("at+jwt").setIssuer("https://auth.acme.dev")
       .setAudience("https://api.acme.dev").setLeeway(Duration.newBuilder().setSeconds(30)).build();
   ```

   An empty type expects `JWT`, and an empty issuer or audience is not checked. Without a leeway, the clock skew of
   the key options is the leeway, 5 seconds by default.

2. Verify the token:

   ```java
   VerifyResponse verified = cryptography.verify(VerifyRequest.newBuilder().setSubject("user:anne")
       .setKey("keys/oauth").setToken(token).setExpected(expected).build()).await();
   ```

   The subject needs `verifier` on the key.

3. Read the result. A token that does not verify is not an error: the response holds a rejection with its reason.

   ```java
   if (verified.hasVerified()) {
     System.out.println(verified.getVerified().getClaims().getFieldsOrThrow("sub").getStringValue());
   } else {
     System.out.println(verified.getRejected().getReason() + ": " + verified.getRejected().getMessage());
   }
   ```

## Verify

The verification prints the subject of the token:

```text
user:anne
```

`verified.getVerified().getKeyVersion()` is `keyVersions/oauth-1`. A token with a changed payload is rejected with
`SIGNATURE`, a token for another audience with `AUDIENCE`, and a token after `exp` and the leeway with `EXPIRED`. For
every reason, see [Status codes](../deployment/status-codes.md#rejections-of-verify).

## Publish the public key

Each version of a signing key has its public key as a JWK, in the field `public_key`, with the `kid` that its tokens
carry. List the versions, and publish the public key of each enabled one in your JWKS:

```java
List<Struct> jwks = cryptography.listKeyVersions(ListKeyVersionsRequest.newBuilder().setSubject("user:anne")
    .setKey("keys/oauth").build()).await().getVersionsList().stream()
    .filter(version -> version.getState() == KeyVersion.State.ENABLED).map(KeyVersion::getPublicKey).toList();
```

`ListKeyVersions` needs `viewer` on the key. Publish the public key of a new version as soon as the key rotates,
because the next token is signed with it.

## Sign data with a MAC key

A MAC key signs bytes as they are, such as a code that you send and check later. It also signs JWTs, with `HS256`,
but has no public key: only Pyrite verifies them.

1. Create a key with the algorithm `HMAC_SHA256`, which gives it the purpose `MAC`:

   ```java
   cryptography.createKey(CreateKeyRequest.newBuilder().setSubject("user:anne").setKeyId("codes")
       .setParent("organizations/acme").setAlgorithm(Algorithm.HMAC_SHA256).build()).await();
   ```

2. Sign the data:

   ```java
   SignResponse mac = cryptography.sign(SignRequest.newBuilder().setSubject("user:anne").setKey("keys/codes")
       .setData(ByteString.copyFromUtf8("482913")).build()).await();
   ```

3. Verify it with the data and the signature. With an empty `key_version`, every enabled version of the key is tried:

   ```java
   VerifyResponse checked = cryptography.verify(VerifyRequest.newBuilder().setSubject("user:anne")
       .setKey("keys/codes").setDetached(Detached.newBuilder().setData(ByteString.copyFromUtf8("482913"))
           .setSignature(mac.getSignature())).build()).await();
   ```

   `checked.hasVerified()` is `true`, and `checked.getVerified().getKeyVersion()` names the version that made the
   signature. Other data gives a rejection with the reason `SIGNATURE`.

## Related

- [About keys and versions](about-keys-and-versions.md)
- [Rotate a key](rotate-a-key.md)
- [Troubleshooting keys](troubleshooting-keys.md)
- [RFC 7519: JSON Web Token](https://www.rfc-editor.org/rfc/rfc7519)
