package dev.horizon.pyrite.exchange;

import com.google.protobuf.ByteString;
import dev.horizon.pyrite.ProviderOptions;
import dev.horizon.pyrite.authentication.v1.ProviderAccount;
import dev.horizon.pyrite.authentication.v1.ProviderAnswer;
import dev.horizon.pyrite.authentication.v1.ProviderRedirect;
import dev.horizon.pyrite.authentication.v1.ProviderRedirects;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.OAuth2AuthorizationURL;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.Oauth2Credentials;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The providers that people sign in at, from the options: the authorization URL of each for a stage, and the trade of
 * the code of a callback for the account at the provider.
 *
 * <p>
 * Each stage that takes a provider gets a random state, PKCE verifier and OpenID Connect nonce, which the exchange
 * keeps with the nonce of the stage, so that they expire with it. A callback whose state is not the stage's is wrong,
 * the code is traded with the verifier, and an ID token whose nonce is not the stage's is wrong; so a code of another
 * exchange, or one that an attacker caught, signs nobody in.
 */
final class ProviderSet {

  private static final int SECRET_BYTES = 32;
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final System.Logger LOGGER = System.getLogger(ProviderSet.class.getName());

  private final Map<String, ProviderOptions> providers = new LinkedHashMap<>();

  ProviderSet(List<ProviderOptions> providers) {
    for (ProviderOptions provider : providers) {
      if (this.providers.putIfAbsent(provider.getName(), provider) != null) {
        throw new IllegalArgumentException("Two providers are named " + provider.getName() + ".");
      }
    }
  }

  boolean isEmpty() {
    return providers.isEmpty();
  }

  /**
   * Makes the state, the PKCE verifier and the nonce of a stage, which the exchange keeps for a verifier.
   */
  void prepare(ExchangeRecord exchange, Verifier<?> verifier) {
    JsonObject secrets = new JsonObject().put("state", random()).put("verifier", random()).put("nonce", random());
    exchange.keep(verifier, ByteString.copyFromUtf8(secrets.encode()));
  }

  /**
   * Gives the authorization URL of each provider, with the state, the PKCE challenge and the nonce of the stage.
   */
  ProviderRedirects redirects(ExchangeRecord exchange, Verifier<?> verifier) {
    ProviderRedirects.Builder redirects = ProviderRedirects.newBuilder();
    secrets(exchange, verifier).ifPresent(secrets -> providers.values().forEach(provider -> redirects.addRedirects(
        ProviderRedirect.newBuilder().setProvider(provider.getName()).setAuthorizationUrl(provider.getAuth()
            .authorizeURL(new OAuth2AuthorizationURL().setRedirectUri(provider.getRedirectUri())
                .setScopes(provider.getScopes()).setState(secrets.getString("state"))
                .setCodeChallenge(challenge(secrets.getString("verifier"))).setCodeChallengeMethod("S256")
                .putAdditionalParameter("nonce", secrets.getString("nonce")))))));
    return redirects.build();
  }

  /**
   * Trades the code of a callback for the account at the provider.
   *
   * @return the account at the provider, or nothing for a wrong answer: an unknown provider, a state that is not the
   * stage's, a code that the provider refuses, or an ID token whose nonce is not the stage's
   */
  Future<Optional<ProviderAccount>> trade(ExchangeRecord exchange, Verifier<?> verifier, ProviderAnswer answer) {
    ProviderOptions provider = providers.get(answer.getProvider());
    Optional<JsonObject> secrets = secrets(exchange, verifier);
    if (provider == null || secrets.isEmpty() || !MessageDigest.isEqual(secrets.get().getString("state")
        .getBytes(StandardCharsets.UTF_8), answer.getState().getBytes(StandardCharsets.UTF_8))) {
      return Future.succeededFuture(Optional.empty());
    }
    return provider.getAuth().authenticate(new Oauth2Credentials().setFlow(OAuth2FlowType.AUTH_CODE)
        .setCode(answer.getCode()).setRedirectUri(provider.getRedirectUri())
        .setCodeVerifier(secrets.get().getString("verifier")))
        .compose(user -> account(provider, user, secrets.get().getString("nonce")))
        .recover(failure -> {
          LOGGER.log(System.Logger.Level.DEBUG, "The provider " + provider.getName() + " refused a code", failure);
          return Future.succeededFuture(Optional.empty());
        });
  }

  /**
   * Reads the account from the ID token of an OpenID Connect provider, whose signature the provider's keys verified,
   * or from the user info of any other provider.
   */
  private static Future<Optional<ProviderAccount>> account(ProviderOptions provider, User user, String nonce) {
    JsonObject idToken = user.attributes().getJsonObject("idToken");
    if (idToken != null) {
      if (!nonce.equals(idToken.getString("nonce")) || idToken.getValue("sub") == null) {
        return Future.succeededFuture(Optional.empty());
      }
      return Future.succeededFuture(Optional.of(account(provider, idToken, String.valueOf(idToken.getValue("sub")))));
    }
    return provider.getAuth().userInfo(user).map(info -> {
      Object id = info.getValue("sub") != null ? info.getValue("sub") : info.getValue("id");
      return id == null ? Optional.empty() : Optional.of(account(provider, info, String.valueOf(id)));
    });
  }

  /**
   * Gives the account at a provider of its claims. Only {@code email_verified} says that the provider verified the
   * email address; any other claim, such as one that a provider gives for a verified account, does not.
   */
  private static ProviderAccount account(ProviderOptions provider, JsonObject claims, String id) {
    ProviderAccount.Builder account = ProviderAccount.newBuilder().setProvider(provider.getName()).setId(id)
        .setEmailVerified(truth(claims.getValue("email_verified")));
    if (claims.getValue("email") instanceof String email) {
      account.setEmail(email);
    }
    return account.build();
  }

  private static boolean truth(Object value) {
    return Boolean.TRUE.equals(value) || "true".equals(value);
  }

  private static Optional<JsonObject> secrets(ExchangeRecord exchange, Verifier<?> verifier) {
    return exchange.nonce().flatMap(nonce -> exchange.state(verifier))
        .map(secrets -> new JsonObject(secrets.toStringUtf8()));
  }

  private static String random() {
    byte[] bytes = new byte[SECRET_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String challenge(String verifier) {
    try {
      return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
          .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("Every Java platform has SHA-256", impossible);
    }
  }
}
