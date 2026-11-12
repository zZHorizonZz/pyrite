package dev.horizon.pyrite.key;

import com.google.crypto.tink.Aead;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/**
 * The root key, which seals the secret of each key version for the database. A sealed secret is bound to the name of
 * its version, so it opens only as that version's.
 *
 * <p>
 * The root key is the Tink {@link Aead} of the key options. A version opens only with the root key that sealed it, so
 * every Pyrite on one database has the same root key.
 */
public final class RootKey {

  private static final byte[] CONTEXT = "pyrite\0".getBytes(StandardCharsets.UTF_8);

  private final Aead aead;

  public RootKey(Aead aead) {
    this.aead = aead;
  }

  byte[] seal(KeyVersionName version, byte[] secret) {
    try {
      return aead.encrypt(secret, bound(version));
    } catch (GeneralSecurityException refused) {
      throw new IllegalStateException("The root key did not seal the version " + version, refused);
    }
  }

  /**
   * Opens the sealed secret of a version.
   *
   * @return the secret
   * @throws IllegalStateException when this root key did not seal it, or sealed it for another version
   */
  byte[] unseal(KeyVersionName version, byte[] sealed) {
    try {
      return aead.decrypt(sealed, bound(version));
    } catch (GeneralSecurityException unreadable) {
      throw new IllegalStateException("The root key does not open the version " + version, unreadable);
    }
  }

  private static byte[] bound(KeyVersionName version) {
    byte[] name = version.name().getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(CONTEXT.length + name.length).put(CONTEXT).put(name).array();
  }
}
