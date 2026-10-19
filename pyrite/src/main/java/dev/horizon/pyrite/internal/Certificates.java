package dev.horizon.pyrite.internal;

import io.vertx.core.Future;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.function.Function;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.TBSCertificate;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;

final class Certificates {

  private static final Instant FOREVER = Instant.parse("9999-12-31T23:59:59Z");
  private static final int SERIAL_BITS = 64;

  private final SecureRandom random = new SecureRandom();

  Future<byte[]> issued(String subject, PublicKey key, Scheme scheme, Instant from,
      Function<byte[], Future<byte[]>> signer) {
    X500Name name = new X500NameBuilder(BCStyle.INSTANCE).addRDN(BCStyle.CN, subject).build();
    AlgorithmIdentifier algorithm = new DefaultSignatureAlgorithmIdentifierFinder().find(scheme.certificateAlgorithm());
    TBSCertificate draft = new X509v3CertificateBuilder(name, new BigInteger(SERIAL_BITS, random), Date.from(from),
        Date.from(FOREVER), name, SubjectPublicKeyInfo.getInstance(key.getEncoded()))
        .build(new Draft(algorithm))
        .toASN1Structure()
        .getTBSCertificate();
    byte[] signed;
    try {
      signed = draft.getEncoded(ASN1Encoding.DER);
    } catch (IOException unwritable) {
      return Future.failedFuture(new IllegalStateException("The certificate cannot be written", unwritable));
    }
    return signer.apply(signed).map(signature -> {
      ASN1EncodableVector certificate = new ASN1EncodableVector();
      certificate.add(draft);
      certificate.add(algorithm);
      certificate.add(new DERBitString(scheme.x509(signature)));
      try {
        return new DERSequence(certificate).getEncoded(ASN1Encoding.DER);
      } catch (IOException unwritable) {
        throw new IllegalStateException("The certificate cannot be written", unwritable);
      }
    });
  }

  private record Draft(AlgorithmIdentifier algorithm) implements ContentSigner {

    @Override
    public AlgorithmIdentifier getAlgorithmIdentifier() {
      return algorithm;
    }

    @Override
    public OutputStream getOutputStream() {
      return OutputStream.nullOutputStream();
    }

    @Override
    public byte[] getSignature() {
      return new byte[0];
    }
  }
}
