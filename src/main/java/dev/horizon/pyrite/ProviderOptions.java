package dev.horizon.pyrite;

import io.vertx.ext.auth.oauth2.OAuth2Auth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A provider that people sign in at, such as Google or GitHub: its name in Pyrite, the Vert.x OAuth2 provider that
 * talks to it, the callback of the application, and the scopes to ask for.
 *
 * <pre>{@code
 * ProviderOptions google = new ProviderOptions().setName("google")
 *     .setAuth(GoogleAuth.create(vertx, clientId, clientSecret))
 *     .setRedirectUri("https://acme.dev/callback/google").addScope("openid").addScope("email");
 * ProviderOptions github = new ProviderOptions().setName("github")
 *     .setAuth(GithubAuth.create(vertx, clientId, clientSecret))
 *     .setRedirectUri("https://acme.dev/callback/github").addScope("read:user");
 * }</pre>
 *
 * <p>
 * The provider of an OpenID Connect issuer gives an ID token, whose {@code sub} is the account's id; build it with
 * discovery, such as {@code GoogleAuth.discover}, so that Vert.x has the keys that verify the token, and ask for the
 * scope {@code openid}. Any other provider gives the account's id as the {@code id} of its user info, such as GitHub
 * and Discord. The client id and secret stay in the Vert.x provider, out of the declaration.
 */
public class ProviderOptions {

  private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]{0,62}");

  private String name;
  private OAuth2Auth auth;
  private String redirectUri;
  private List<String> scopes;

  /**
   * Creates options without a name, a provider, a callback or scopes.
   */
  public ProviderOptions() {
    scopes = new ArrayList<>();
  }

  /**
   * Creates a copy of options, with the same Vert.x provider and a copy of their scopes.
   *
   * @param other the options to copy
   */
  public ProviderOptions(ProviderOptions other) {
    name = other.name;
    auth = other.auth;
    redirectUri = other.redirectUri;
    scopes = new ArrayList<>(other.scopes);
  }

  /**
   * Gives the name.
   *
   * @return the name, or {@code null} when none is set
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the name of the provider, which a {@code ProviderAnswer} names, and which the factors that link its accounts
   * keep. Do not rename a provider that accounts are linked at.
   *
   * @param name the name, such as {@code google}, matching {@code [a-z][a-z0-9-]{0,62}}
   * @return these options
   * @throws IllegalArgumentException if the name does not match
   */
  public ProviderOptions setName(String name) {
    if (!NAME.matcher(Objects.requireNonNull(name, "name")).matches()) {
      throw new IllegalArgumentException("The name of a provider matches " + NAME.pattern() + ".");
    }
    this.name = name;
    return this;
  }

  /**
   * Gives the Vert.x provider.
   *
   * @return the provider, or {@code null} when none is set
   */
  public OAuth2Auth getAuth() {
    return auth;
  }

  /**
   * Sets the Vert.x provider, with the client id and secret that the provider gave the application.
   *
   * @param auth the provider, such as {@code GoogleAuth.create(vertx, clientId, clientSecret)}
   * @return these options
   */
  public ProviderOptions setAuth(OAuth2Auth auth) {
    this.auth = Objects.requireNonNull(auth, "auth");
    return this;
  }

  /**
   * Gives the callback.
   *
   * @return the URI, or {@code null} when none is set
   */
  public String getRedirectUri() {
    return redirectUri;
  }

  /**
   * Sets the callback of the application, which the provider sends the person back to, with a code and a state, and
   * which the application registered at the provider.
   *
   * @param redirectUri the URI, such as {@code https://acme.dev/callback/google}
   * @return these options
   */
  public ProviderOptions setRedirectUri(String redirectUri) {
    this.redirectUri = Objects.requireNonNull(redirectUri, "redirectUri");
    return this;
  }

  /**
   * Gives the scopes.
   *
   * @return the scopes, which a change changes in these options
   */
  public List<String> getScopes() {
    return scopes;
  }

  /**
   * Sets the scopes to ask for, in place of those before.
   *
   * @param scopes the scopes, such as {@code openid} and {@code email}; none by default
   * @return these options
   */
  public ProviderOptions setScopes(List<String> scopes) {
    this.scopes = new ArrayList<>(scopes);
    return this;
  }

  /**
   * Adds a scope to ask for.
   *
   * @param scope the scope, such as {@code openid}
   * @return these options
   */
  public ProviderOptions addScope(String scope) {
    scopes.add(Objects.requireNonNull(scope, "scope"));
    return this;
  }
}
