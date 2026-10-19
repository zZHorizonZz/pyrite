package dev.horizon.pyrite.internal;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
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
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERSequence;

public enum Scheme {
  RS256("RSA", "SHA256withRSA", "SHA256WITHRSA", JWSAlgorithm.RS256),
  ES256("EC", "SHA256withECDSAinP1363Format", "SHA256WITHECDSA", JWSAlgorithm.ES256),
  EDDSA("Ed25519", "Ed25519", "ED25519", JWSAlgorithm.EdDSA);

  private static final int RSA_BITS = 2048;
  private static final int P256_BYTES = 32;
  private static final int ED25519_BYTES = 32;

  private final String keyAlgorithm;
  private final String signatureAlgorithm;
  private final String certificateAlgorithm;
  private final JWSAlgorithm jose;

  Scheme(String keyAlgorithm, String signatureAlgorithm, String certificateAlgorithm, JWSAlgorithm jose) {
    this.keyAlgorithm = keyAlgorithm;
    this.signatureAlgorithm = signatureAlgorithm;
    this.certificateAlgorithm = certificateAlgorithm;
    this.jose = jose;
  }

  public JWSAlgorithm jose() {
    return jose;
  }

  public String certificateAlgorithm() {
    return certificateAlgorithm;
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
      case ES256 -> key instanceof ECPublicKey ec && ec.getParams().getCurve().getField().getFieldSize() == 256;
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

  public byte[] x509(byte[] signature) {
    if (this != ES256) {
      return signature;
    }
    try {
      return new DERSequence(new ASN1Encodable[] {
          new ASN1Integer(new BigInteger(1, Arrays.copyOfRange(signature, 0, P256_BYTES))),
          new ASN1Integer(new BigInteger(1, Arrays.copyOfRange(signature, P256_BYTES, 2 * P256_BYTES)))
      }).getEncoded(ASN1Encoding.DER);
    } catch (IOException unwritable) {
      throw new IllegalStateException("The signature cannot be written as DER", unwritable);
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
