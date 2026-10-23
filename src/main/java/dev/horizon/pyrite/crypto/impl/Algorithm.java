package dev.horizon.pyrite.crypto.impl;

import com.nimbusds.jose.JWSAlgorithm;

enum Algorithm {
  AES256_GCM("ENCRYPTION", null, null),
  HMAC_SHA256("MAC", null, JWSAlgorithm.HS256),
  RS256("SIGNING", Scheme.RS256, JWSAlgorithm.RS256),
  ES256("SIGNING", Scheme.ES256, JWSAlgorithm.ES256),
  EDDSA("SIGNING", Scheme.EDDSA, JWSAlgorithm.EdDSA);

  private final String purpose;
  private final Scheme scheme;
  private final JWSAlgorithm jose;

  Algorithm(String purpose, Scheme scheme, JWSAlgorithm jose) {
    this.purpose = purpose;
    this.scheme = scheme;
    this.jose = jose;
  }

  JWSAlgorithm jose() {
    return jose;
  }

  public String purpose() {
    return purpose;
  }

  Scheme scheme() {
    return scheme;
  }
}
