package dev.horizon.pyrite.internal;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import io.vertx.core.json.JsonObject;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

public enum Scheme {
  RS256("RSA", "SHA256withRSA", JWSAlgorithm.RS256),
  ES256("EC", "SHA256withECDSAinP1363Format", JWSAlgorithm.ES256),
  EDDSA("Ed25519", "Ed25519", JWSAlgorithm.EdDSA);

  private static final int RSA_BITS = 2048;
  private static final ECParameterSpec P256;
  private static final int ED25519_BYTES = 32;

  static {
    try {
      AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
      parameters.init(new ECGenParameterSpec("secp256r1"));
      P256 = parameters.getParameterSpec(ECParameterSpec.class);
    } catch (GeneralSecurityException missing) {
      throw new IllegalStateException("The JDK does not know the curve P-256", missing);
    }
  }

  private final String keyAlgorithm;
  private final String signatureAlgorithm;
  private final JWSAlgorithm jose;

  Scheme(String keyAlgorithm, String signatureAlgorithm, JWSAlgorithm jose) {
    this.keyAlgorithm = keyAlgorithm;
    this.signatureAlgorithm = signatureAlgorithm;
    this.jose = jose;
  }

  public JWSAlgorithm jose() {
    return jose;
  }

  public KeyPair generated() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance(keyAlgorithm);
      switch (this) {
        case RS256 -> generator.initialize(RSA_BITS);
        case ES256 -> generator.initialize(new ECGenParameterSpec("secp256r1"));
        case EDDSA -> {
        }
      }
      return generator.generateKeyPair();
    } catch (GeneralSecurityException missing) {
      throw new IllegalStateException("The JDK cannot generate " + keyAlgorithm + " keys", missing);
    }
  }

  public PublicKey publicKey(byte[] subjectPublicKeyInfo) {
    try {
      return KeyFactory.getInstance(keyAlgorithm).generatePublic(new X509EncodedKeySpec(subjectPublicKeyInfo));
    } catch (GeneralSecurityException unreadable) {
      throw new IllegalStateException("The public key is not an X.509 " + keyAlgorithm + " key", unreadable);
    }
  }

  public PrivateKey privateKey(byte[] pkcs8) {
    try {
      return KeyFactory.getInstance(keyAlgorithm).generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    } catch (GeneralSecurityException unreadable) {
      throw new IllegalStateException("The private key is not a PKCS#8 " + keyAlgorithm + " key", unreadable);
    }
  }

  public boolean accepts(PublicKey key) {
    return switch (this) {
      case RS256 -> key instanceof RSAPublicKey rsa && rsa.getModulus().bitLength() >= RSA_BITS;
      case ES256 -> key instanceof ECPublicKey ec && ec.getParams().getCurve().equals(P256.getCurve())
          && ec.getParams().getGenerator().equals(P256.getGenerator()) && ec.getParams().getOrder().equals(P256.getOrder())
          && ec.getParams().getCofactor() == P256.getCofactor();
      case EDDSA -> key instanceof EdECPublicKey ed && ed.getParams().getName().equals("Ed25519");
    };
  }

  public byte[] sign(PrivateKey key, byte[] data) {
    try {
      Signature signature = Signature.getInstance(signatureAlgorithm);
      signature.initSign(key);
      signature.update(data);
      return signature.sign();
    } catch (GeneralSecurityException refused) {
      throw new IllegalStateException("The key did not sign with " + signatureAlgorithm, refused);
    }
  }

  public boolean verifies(PublicKey key, byte[] data, byte[] signed) {
    try {
      Signature signature = Signature.getInstance(signatureAlgorithm);
      signature.initVerify(key);
      signature.update(data);
      return signature.verify(signed);
    } catch (GeneralSecurityException unverifiable) {
      return false;
    }
  }

  public JsonObject jwk(PublicKey key, String id) {
    return new JsonObject(switch (this) {
      case RS256 -> new RSAKey.Builder((RSAPublicKey) key).keyID(id).algorithm(jose).keyUse(KeyUse.SIGNATURE).build()
          .toJSONObject();
      case ES256 -> new ECKey.Builder(Curve.P_256, (ECPublicKey) key).keyID(id).algorithm(jose)
          .keyUse(KeyUse.SIGNATURE).build().toJSONObject();
      case EDDSA -> {
        byte[] encoded = key.getEncoded();
        byte[] x = Arrays.copyOfRange(encoded, encoded.length - ED25519_BYTES, encoded.length);
        yield new OctetKeyPair.Builder(Curve.Ed25519, Base64URL.encode(x)).keyID(id).algorithm(jose)
            .keyUse(KeyUse.SIGNATURE).build().toJSONObject();
      }
    });
  }
}
