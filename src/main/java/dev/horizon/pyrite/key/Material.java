package dev.horizon.pyrite.key;

import dev.horizon.pyrite.PyriteException;
import io.vertx.grpc.common.GrpcStatus;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The material of a key version, unsealed: an AES key, an HMAC key, or a key pair. Its secret is what the root key
 * seals for the database: the key itself, or the private key in PKCS#8.
 */
sealed interface Material permits Material.Aes, Material.Signing {

  byte[] secret();

  sealed interface Signing extends Material permits Hmac, Pair {

    byte[] sign(byte[] data);

    boolean verifies(byte[] data, byte[] signature);
  }

  record Aes(byte[] key) implements Material {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final SecureRandom RANDOM = new SecureRandom();

    public Aes {
      key = key.clone();
    }

    @Override
    public byte[] key() {
      return key.clone();
    }

    @Override
    public byte[] secret() {
      return key.clone();
    }

    byte[] encrypt(byte[] plaintext, byte[] associatedData) {
      byte[] nonce = new byte[NONCE_BYTES];
      RANDOM.nextBytes(nonce);
      try {
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(associatedData);
        byte[] body = cipher.doFinal(plaintext);
        return ByteBuffer.allocate(NONCE_BYTES + body.length).put(nonce).put(body).array();
      } catch (GeneralSecurityException failure) {
        throw new IllegalStateException("AES-GCM did not encrypt", failure);
      }
    }

    byte[] decrypt(byte[] ciphertext, byte[] associatedData) {
      try {
        if (ciphertext.length < NONCE_BYTES) {
          throw new GeneralSecurityException("The ciphertext is shorter than its nonce.");
        }
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
            new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(ciphertext, 0, NONCE_BYTES)));
        cipher.updateAAD(associatedData);
        return cipher.doFinal(ciphertext, NONCE_BYTES, ciphertext.length - NONCE_BYTES);
      } catch (GeneralSecurityException unreadable) {
        throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The ciphertext was not made by this key with that"
            + " associated data, or was changed.");
      }
    }
  }

  record Hmac(byte[] key) implements Signing {

    private static final String ALGORITHM = "HmacSHA256";

    public Hmac {
      key = key.clone();
    }

    @Override
    public byte[] key() {
      return key.clone();
    }

    @Override
    public byte[] secret() {
      return key.clone();
    }

    @Override
    public boolean verifies(byte[] data, byte[] signature) {
      return MessageDigest.isEqual(sign(data), signature);
    }

    @Override
    public byte[] sign(byte[] data) {
      try {
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(new SecretKeySpec(key, ALGORITHM));
        return mac.doFinal(data);
      } catch (GeneralSecurityException failure) {
        throw new IllegalStateException("HMAC-SHA256 did not run", failure);
      }
    }
  }

  record Pair(Scheme scheme, KeyPair pair) implements Signing {

    @Override
    public byte[] secret() {
      return pair.getPrivate().getEncoded();
    }

    @Override
    public byte[] sign(byte[] data) {
      return scheme.sign(pair.getPrivate(), data);
    }

    @Override
    public boolean verifies(byte[] data, byte[] signature) {
      return scheme.verifies(pair.getPublic(), data, signature);
    }

    PublicKey publicKey() {
      return pair.getPublic();
    }
  }
}
