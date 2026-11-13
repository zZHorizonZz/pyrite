package dev.horizon.pyrite;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The WebAuthn relying party that passkeys are registered with: the application's domain, the name that
 * authenticators show, and the origins that passkey answers may come from.
 *
 * <pre>{@code
 * RelyingPartyOptions relyingParty = new RelyingPartyOptions().setId("acme.dev").setName("Acme")
 *     .addOrigin("https://acme.dev").addOrigin("https://app.acme.dev");
 * }</pre>
 *
 * <p>
 * A passkey answer from an origin that the options do not name is wrong.
 */
public class RelyingPartyOptions {

  private String id;
  private String name;
  private List<String> origins;

  /**
   * Creates options without an id, a name or origins.
   */
  public RelyingPartyOptions() {
    origins = new ArrayList<>();
  }

  /**
   * Creates a copy of options, with a copy of their origins.
   *
   * @param other the options to copy
   */
  public RelyingPartyOptions(RelyingPartyOptions other) {
    id = other.id;
    name = other.name;
    origins = new ArrayList<>(other.origins);
  }

  /**
   * Gives the relying party id.
   *
   * @return the id, or {@code null} when none is set
   */
  public String getId() {
    return id;
  }

  /**
   * Sets the relying party id, the domain that passkeys belong to.
   *
   * @param id the domain, such as {@code acme.dev}; none by default
   * @return these options
   */
  public RelyingPartyOptions setId(String id) {
    this.id = Objects.requireNonNull(id, "id");
    return this;
  }

  /**
   * Gives the name that authenticators show.
   *
   * @return the name, or {@code null} when none is set
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the name that authenticators show.
   *
   * @param name the name, such as {@code Acme}; none by default
   * @return these options
   */
  public RelyingPartyOptions setName(String name) {
    this.name = Objects.requireNonNull(name, "name");
    return this;
  }

  /**
   * Gives the origins that passkey answers may come from.
   *
   * @return the origins, which a change changes in these options
   */
  public List<String> getOrigins() {
    return origins;
  }

  /**
   * Sets the origins that passkey answers may come from, in place of those before.
   *
   * @param origins the origins, such as {@code https://acme.dev}; none by default
   * @return these options
   */
  public RelyingPartyOptions setOrigins(List<String> origins) {
    this.origins = new ArrayList<>(origins);
    return this;
  }

  /**
   * Adds an origin that passkey answers may come from.
   *
   * @param origin the origin, the scheme, the host and any port, such as {@code https://acme.dev}
   * @return these options
   */
  public RelyingPartyOptions addOrigin(String origin) {
    origins.add(Objects.requireNonNull(origin, "origin"));
    return this;
  }
}
