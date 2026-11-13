package dev.horizon.pyrite;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import io.vertx.ext.auth.oauth2.providers.OpenIDConnectAuth;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A provider that people sign in at, for the tests, on a local HTTP server: an OpenID Connect issuer with discovery,
 * keys and ID tokens, which also answers as a provider without ID tokens, with user info. Its token endpoint takes a
 * code once, and only with the PKCE verifier of the challenge that the code was made for.
 *
 * <p>
 * A test signs a person in with {@link #signIn}, which reads the authorization URL that Pyrite gave as a browser would,
 * and gives the code and the state that the provider sends to the callback.
 */
public final class FakeProvider {

  public static final String CLIENT_ID = "pyrite";
  public static final String CLIENT_SECRET = "a secret of the client";

  /**
   * What the provider sends to the callback of the application.
   */
  public record Callback(String code, String state) {
  }

  /**
   * What a test changes of a sign-in, as an attacker would.
   */
  public enum Forgery {
    /** Nothing. */
    NONE,
    /** The state of another exchange. */
    STATE,
    /** An ID token with another nonce. */
    NONCE,
    /** A code made for another PKCE challenge, such as one that an attacker caught. */
    CODE_CHALLENGE,
    /** An email address that the provider did not verify, as at a provider that takes any address that a person types. */
    UNVERIFIED_EMAIL
  }

  private record Grant(String subject, String email, boolean emailVerified, String challenge, String redirectUri,
      String nonce, boolean openId) {
  }

  private final HttpServer server;
  private final RSAKey key;
  private final String issuer;
  private final Map<String, Grant> codes = new ConcurrentHashMap<>();
  private final Map<String, Grant> tokens = new ConcurrentHashMap<>();

  private FakeProvider(Vertx vertx) throws Exception {
    key = new RSAKeyGenerator(2048).keyID("fake").generate();
    server = vertx.createHttpServer().requestHandler(this::handle).listen(0, "localhost").await(TIMEOUT);
    issuer = "http://localhost:" + server.actualPort();
  }

  /**
   * Starts the provider on a free port.
   */
  public static FakeProvider start(Vertx vertx) throws Exception {
    return new FakeProvider(vertx);
  }

  /**
   * Gives the provider as an OpenID Connect issuer, found by discovery, as {@code GoogleAuth.discover} gives Google.
   */
  public OAuth2Auth openIdConnect(Vertx vertx) throws Exception {
    return OpenIDConnectAuth.discover(vertx, new OAuth2Options().setSite(issuer).setClientId(CLIENT_ID)
        .setClientSecret(CLIENT_SECRET)).await(TIMEOUT);
  }

  /**
   * Gives the provider as one without ID tokens, whose user info names the account, as {@code GithubAuth} gives GitHub.
   */
  public OAuth2Auth oauth(Vertx vertx) {
    return OAuth2Auth.create(vertx, new OAuth2Options().setSite(issuer).setAuthorizationPath("/authorize")
        .setTokenPath("/token").setUserInfoPath("/userinfo").setClientId(CLIENT_ID).setClientSecret(CLIENT_SECRET));
  }

  /**
   * Signs a person in at the authorization URL that Pyrite gave, and gives what the provider sends to the callback.
   *
   * @param authorizationUrl the URL, with the state, the PKCE challenge and the nonce of the stage
   * @param subject the id of the person's account at the provider, which is also its email address when it holds an
   * {@code @}
   * @param forgery what to change, as an attacker would
   */
  public Callback signIn(String authorizationUrl, String subject, Forgery forgery) {
    Map<String, String> query = query(URI.create(authorizationUrl).getRawQuery());
    String code = UUID.randomUUID().toString();
    codes.put(code, new Grant(subject, subject.contains("@") ? subject : subject + "@example.com",
        forgery != Forgery.UNVERIFIED_EMAIL,
        forgery == Forgery.CODE_CHALLENGE ? "another challenge" : query.get("code_challenge"),
        query.get("redirect_uri"), forgery == Forgery.NONCE ? "another nonce" : query.get("nonce"),
        query.getOrDefault("scope", "").contains("openid")));
    return new Callback(code, forgery == Forgery.STATE ? "another state" : query.get("state"));
  }

  public void close() throws Exception {
    server.close().await(TIMEOUT);
  }

  private void handle(HttpServerRequest request) {
    switch (request.path()) {
      case "/.well-known/openid-configuration" -> json(request, 200, new JsonObject().put("issuer", issuer)
          .put("authorization_endpoint", issuer + "/authorize").put("token_endpoint", issuer + "/token")
          .put("userinfo_endpoint", issuer + "/userinfo").put("jwks_uri", issuer + "/jwks")
          .put("response_types_supported", new JsonArray().add("code"))
          .put("id_token_signing_alg_values_supported", new JsonArray().add("RS256")));
      case "/jwks" -> json(request, 200, new JsonObject().put("keys", new JsonArray()
          .add(new JsonObject(key.toPublicJWK().toJSONObject()))));
      case "/token" -> request.body().onSuccess(body -> token(request, query(body.toString())));
      case "/userinfo" -> {
        String bearer = request.getHeader("Authorization");
        Grant grant = bearer == null ? null : tokens.get(bearer.substring("Bearer ".length()));
        if (grant == null) {
          json(request, 401, new JsonObject().put("error", "invalid_token"));
        } else {
          json(request, 200, new JsonObject().put("id", grant.subject()).put("email", grant.email())
              .put("verified", grant.emailVerified()));
        }
      }
      default -> request.response().setStatusCode(404).end();
    }
  }

  private void token(HttpServerRequest request, Map<String, String> form) {
    Grant grant = codes.remove(form.getOrDefault("code", ""));
    if (grant == null || !grant.redirectUri().equals(form.get("redirect_uri"))
        || !grant.challenge().equals(challenge(form.getOrDefault("code_verifier", "")))) {
      json(request, 400, new JsonObject().put("error", "invalid_grant"));
      return;
    }
    String accessToken = UUID.randomUUID().toString();
    tokens.put(accessToken, grant);
    JsonObject response = new JsonObject().put("access_token", accessToken).put("token_type", "Bearer")
        .put("expires_in", 3600);
    if (grant.openId()) {
      response.put("id_token", idToken(grant));
    }
    json(request, 200, response);
  }

  private String idToken(Grant grant) {
    try {
      Instant now = Instant.now();
      SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID())
          .type(JOSEObjectType.JWT).build(),
          new JWTClaimsSet.Builder().issuer(issuer).audience(CLIENT_ID)
              .subject(grant.subject()).claim("email", grant.email()).claim("email_verified", grant.emailVerified())
              .claim("nonce", grant.nonce()).issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300)))
              .build());
      jwt.sign(new RSASSASigner(key));
      return jwt.serialize();
    } catch (Exception impossible) {
      throw new IllegalStateException("The fake provider could not sign an ID token", impossible);
    }
  }

  private static void json(HttpServerRequest request, int status, JsonObject body) {
    request.response().setStatusCode(status).putHeader("Content-Type", "application/json").end(body.encode());
  }

  private static Map<String, String> query(String text) {
    Map<String, String> query = new HashMap<>();
    if (text == null || text.isEmpty()) {
      return query;
    }
    for (String pair : text.split("&")) {
      int equals = pair.indexOf('=');
      query.put(URLDecoder.decode(equals < 0 ? pair : pair.substring(0, equals), StandardCharsets.UTF_8),
          equals < 0 ? "" : URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
    }
    return query;
  }

  private static String challenge(String verifier) {
    try {
      return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
          .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    } catch (Exception impossible) {
      throw new IllegalStateException("Every Java platform has SHA-256", impossible);
    }
  }
}
