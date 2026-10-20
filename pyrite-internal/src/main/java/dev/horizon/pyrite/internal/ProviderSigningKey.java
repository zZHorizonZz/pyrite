package dev.horizon.pyrite.internal;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.util.Base64URL;
import dev.horizon.pyrite.Capability;
import dev.horizon.pyrite.Expected;
import dev.horizon.pyrite.Export;
import dev.horizon.pyrite.Rejection;
import dev.horizon.pyrite.Signature;
import dev.horizon.pyrite.Signing;
import dev.horizon.pyrite.SigningKey;
import dev.horizon.pyrite.State;
import dev.horizon.pyrite.Verification;
import dev.horizon.pyrite.Version;
import dev.horizon.pyrite.common.InvalidArgumentException;
import dev.horizon.pyrite.common.Violation;
import dev.horizon.pyrite.spi.KeyHandle;
import dev.horizon.pyrite.spi.VersionHandle;
import io.vertx.core.Future;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.security.PublicKey;
import java.text.ParseException;
import java.time.Instant;
import java.util.List;

final class ProviderSigningKey extends ProviderKey implements SigningKey {

  private static final String JWT = "JWT";

  private final Scheme scheme;

  ProviderSigningKey(ProviderKeyring keyring, KeyHandle key, Lifecycle lifecycle) {
    super(keyring, key, lifecycle);
    this.scheme = Scheme.valueOf(((Signing) key.purpose()).algorithm().name());
  }

  @Override
  public Signing purpose() {
    return (Signing) key.purpose();
  }

  @Override
  public Future<String> jwt(JsonObject claims) {
    return jwt(JWT, claims);
  }

  @Override
  public Future<String> jwt(String type, JsonObject claims) {
    return lifecycle.settled().compose(state -> {
      VersionHandle primary = state.current();
      JWSObject draft = new JWSObject(new JWSHeader.Builder(scheme.jose())
          .type(new JOSEObjectType(type))
          .keyID(primary.id())
          .build(), new Payload(claims.encode()));
      return primary.sign(draft.getSigningInput())
          .map(signature -> draft.getHeader().toBase64URL() + "." + draft.getPayload().toBase64URL() + "."
              + Base64URL.encode(signature));
    });
  }

  @Override
  public Future<Signature> sign(byte[] data) {
    return lifecycle.settled().compose(state -> {
      VersionHandle primary = state.current();
      return primary.sign(data).map(signature -> new Signature(primary.id(), signature));
    });
  }

  @Override
  public Future<Verification> verify(String token, Expected expected) {
    JWSObject jws;
    try {
      jws = JWSObject.parse(token);
    } catch (ParseException malformed) {
      return rejected(Rejection.MALFORMED, "The token is not a JWS in compact serialization");
    }
    JWSHeader header = jws.getHeader();
    if (header.getCriticalParams() != null && !header.getCriticalParams().isEmpty()) {
      return rejected(Rejection.MALFORMED, "The token names critical header parameters, which Pyrite does not support");
    }
    if (!scheme.jose().equals(header.getAlgorithm())) {
      return rejected(Rejection.ALGORITHM, "The token is not signed with " + scheme.jose() + ", the algorithm of the "
          + "key " + key.name());
    }
    if (header.getType() == null || !expected.type().equalsIgnoreCase(header.getType().getType())) {
      return rejected(Rejection.TYPE, "The token is not of type " + expected.type());
    }
    if (header.getKeyID() == null) {
      return rejected(Rejection.UNKNOWN_VERSION, "The token names no key id");
    }
    byte[] input = jws.getSigningInput();
    byte[] signature = jws.getSignature().decode();
    return lifecycle.settled().compose(state -> verified(state, header.getKeyID(), input, signature)
        .map(verification -> switch (verification) {
          case Verification.Rejected rejected -> rejected;
          case Verification.Verified verified -> claimed(verified.version(), jws.getPayload().toString(), expected,
              state.now());
        }));
  }

  @Override
  public Future<Verification> verify(byte[] data, Signature signature) {
    return lifecycle.settled().compose(state -> verified(state, signature.version(), data, signature.bytes()));
  }

  @Override
  public Future<JsonObject> published() {
    return lifecycle.settled().compose(state -> {
      List<VersionHandle> published = state.versions().stream().filter(version -> version.state().live()).toList();
      return Future.all(published.stream().map(version -> keyring.publicKey(key.name(), version)).toList())
          .map(keys -> {
            JsonArray jwks = new JsonArray();
            for (int i = 0; i < published.size(); i++) {
              jwks.add(scheme.jwk(keys.<PublicKey> resultAt(i), published.get(i).id()));
            }
            return new JsonObject().put("keys", jwks);
          });
    });
  }

  @Override
  public Future<Export<Version>> export() {
    if (!keyring.capabilities().has(Capability.EXPORTED_VERSIONS)) {
      return Future.failedFuture(keyring.unimplemented("export the private key of a version"));
    }
    return lifecycle.read()
        .compose(state -> state.key().export(new State.Live(), lifecycle.expiry(state.key().validity(), state.now()),
            state.key().revision()))
        .map(exported -> new Export<>(version(exported.version()), exported.privateKey()));
  }

  @Override
  public Future<Version> upload(PublicKey publicKey) {
    if (!keyring.capabilities().has(Capability.UPLOADED_VERSIONS)) {
      return Future.failedFuture(keyring.unimplemented("take public keys"));
    }
    if (!scheme.accepts(publicKey)) {
      return Future.failedFuture(new InvalidArgumentException("The public key is not a key of " + purpose().algorithm(),
          List.of(new Violation("publicKey", "a " + purpose().algorithm() + " public key"))));
    }
    return lifecycle.read()
        .compose(state -> state.key().upload(publicKey, new State.Live(),
            lifecycle.expiry(state.key().validity(), state.now()), state.key().revision()))
        .map(this::version);
  }

  private Future<Verification> verified(Snapshot state, String id, byte[] data, byte[] signature) {
    VersionHandle version = state.version(id).orElse(null);
    if (version == null) {
      return rejected(Rejection.UNKNOWN_VERSION, "The key " + key.name() + " has no version with the id that the token names");
    }
    if (!version.state().live()) {
      return rejected(version.state() instanceof State.Expired ? Rejection.EXPIRED_VERSION : Rejection.DISABLED_VERSION,
          "The version " + id + " of " + key.name() + " is " + version.state());
    }
    return keyring.publicKey(key.name(), version).map(publicKey -> scheme.verifies(publicKey, data, signature)
        ? new Verification.Verified(version(version), new JsonObject())
        : new Verification.Rejected(Rejection.SIGNATURE, "The signature was not made by the version " + id));
  }

  private Verification claimed(Version version, String payload, Expected expected, Instant at) {
    JsonObject claims;
    try {
      claims = new JsonObject(payload);
    } catch (DecodeException | ClassCastException malformed) {
      return new Verification.Rejected(Rejection.MALFORMED, "The claims of the token are not a JSON object");
    }
    if (!expected.issuer().equals(claims.getValue("iss"))) {
      return new Verification.Rejected(Rejection.ISSUER, "The token is not issued by " + expected.issuer());
    }
    Object audience = claims.getValue("aud");
    boolean addressed = audience instanceof String single
        ? single.equals(expected.audience())
        : audience instanceof JsonArray many && many.contains(expected.audience());
    if (!addressed) {
      return new Verification.Rejected(Rejection.AUDIENCE, "The token is not for " + expected.audience());
    }
    long now = at.getEpochSecond();
    long leeway = expected.leeway().toSeconds();
    if (!(claims.getValue("exp") instanceof Number expires)) {
      return new Verification.Rejected(Rejection.EXPIRED, "The token has no exp");
    }
    if (now > expires.longValue() + leeway) {
      return new Verification.Rejected(Rejection.EXPIRED, "The token expired at " + expires);
    }
    if (claims.getValue("nbf") instanceof Number notBefore && now + leeway < notBefore.longValue()) {
      return new Verification.Rejected(Rejection.NOT_YET_VALID, "The token is valid from " + notBefore);
    }
    return new Verification.Verified(version, claims);
  }

  private Future<Verification> rejected(Rejection reason, String description) {
    return Future.succeededFuture(new Verification.Rejected(reason, description));
  }
}
