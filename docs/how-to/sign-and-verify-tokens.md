# Sign and verify tokens

Sign claims as a JWT with a signing key, such as an access token that your gateway issues, and verify it later. Other
parties can verify the token with the key's public key, without Pyrite.

## Before you begin

- Declare `pyrite.dev/Key`, and give the services a root key. For more information, refer to
  [Encrypt data](encrypt-data.md#before-you-begin).
- Add the cryptography service to your server with `CryptographyService.create`.

## Sign a JWT

1. Create a signing key. `ES256` signs with ECDSA on P-256:

   ```java
   data.createResource(CreateResourceRequest.newBuilder().setSubject("user:anne").setParent("organizations/acme")
       .setResourceId("oauth").setResource(Resource.newBuilder().setType("pyrite.dev/Key")
           .putFields("purpose", Value.newBuilder().setStringValue("SIGNING").build())
           .putFields("algorithm", Value.newBuilder().setStringValue("ES256").build()))
       .build()).await();
   ```

2. Sign the claims, with the token's `typ`. Include `exp`, because verification rejects a token without it:

   ```java
   long now = Instant.now().getEpochSecond();
   Struct claims = Struct.newBuilder()
       .putFields("iss", com.google.protobuf.Value.newBuilder().setStringValue("https://auth.acme.dev").build())
       .putFields("aud", com.google.protobuf.Value.newBuilder().setStringValue("https://api.acme.dev").build())
       .putFields("sub", com.google.protobuf.Value.newBuilder().setStringValue("user:anne").build())
       .putFields("exp", com.google.protobuf.Value.newBuilder().setNumberValue(now + 600).build()).build();
   SignResponse signed = cryptography.sign(SignRequest.newBuilder().setSubject("user:anne")
       .setKey("keys/oauth").setClaims(claims).setType("at+jwt").build()).await();
   String token = signed.getToken();
   ```

   The header's `kid` is the id of the version that signed, such as `oauth-1`.

## Verify a JWT

1. Name what the token must be: its `typ`, its issuer, an audience, and the clock skew that you allow:

   ```java
   Expected expected = Expected.newBuilder().setType("at+jwt").setIssuer("https://auth.acme.dev")
       .setAudience("https://api.acme.dev").setLeeway(Duration.newBuilder().setSeconds(30)).build();
   ```

2. Verify the token:

   ```java
   VerifyResponse verified = cryptography.verify(VerifyRequest.newBuilder().setSubject("user:anne")
       .setKey("keys/oauth").setToken(token).setExpected(expected).build()).await();
   ```

3. Read the result. A token that does not verify is not an error: the response holds a rejection with its reason.

   ```java
   if (verified.hasVerified()) {
     System.out.println(verified.getVerified().getClaims().getFieldsOrThrow("sub").getStringValue());
   } else {
     System.out.println(verified.getRejected().getReason());
   }
   ```

   ```txt
   user:anne
   ```

For every reason, refer to [Status codes](../reference/status-codes.md#rejections-of-verify).

## Publish the public key

Each version of a signing key holds its public key as a JWK in the field `public_key`. Read it from the version that
the token's `kid` names, and publish it in your JWKS:

```java
Resource version = data.getResource(GetResourceRequest.newBuilder().setSubject("user:anne")
    .setName("keyVersions/oauth-1").build()).await();
MapValue jwk = version.getFieldsMap().get("public_key").getMapValue();
```

## Sign data with a MAC key

A MAC key signs bytes as they are, such as a code that you send and check later.

1. Create a key with the purpose `MAC` and the algorithm `HMAC_SHA256`.

2. Sign the data:

   ```java
   SignResponse mac = cryptography.sign(SignRequest.newBuilder().setSubject("user:anne").setKey("keys/codes")
       .setData(ByteString.copyFromUtf8("482913")).build()).await();
   ```

3. Verify it with the data and the signature:

   ```java
   VerifyResponse checked = cryptography.verify(VerifyRequest.newBuilder().setSubject("user:anne")
       .setKey("keys/codes").setDetached(Detached.newBuilder().setData(ByteString.copyFromUtf8("482913"))
           .setSignature(mac.getSignature())).build()).await();
   ```

   `checked.hasVerified()` is `true`. Other data gives a rejection with the reason `SIGNATURE`.

## Related resources

- [Keys and versions](../concepts/keys-and-versions.md)
- [RFC 7519: JSON Web Token](https://www.rfc-editor.org/rfc/rfc7519)
