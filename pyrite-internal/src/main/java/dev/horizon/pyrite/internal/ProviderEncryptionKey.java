package dev.horizon.pyrite.internal;

import dev.horizon.pyrite.Encryption;
import dev.horizon.pyrite.EncryptionKey;
import dev.horizon.pyrite.Reason;
import dev.horizon.pyrite.common.FailedPreconditionException;
import dev.horizon.pyrite.spi.KeyHandle;
import dev.horizon.pyrite.spi.VersionHandle;
import io.vertx.core.Future;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class ProviderEncryptionKey extends ProviderKey implements EncryptionKey {

  ProviderEncryptionKey(ProviderKeyring keyring, KeyHandle key, Lifecycle lifecycle) {
    super(keyring, key, lifecycle);
  }

  @Override
  public Encryption purpose() {
    return (Encryption) key.purpose();
  }

  @Override
  public Future<byte[]> encrypt(byte[] plaintext, String context) {
    return lifecycle.settled().compose(state -> {
      VersionHandle primary = state.current();
      byte[] id = primary.id().getBytes(StandardCharsets.US_ASCII);
      return primary.encrypt(plaintext, bound(context))
          .map(ciphertext -> ByteBuffer.allocate(1 + id.length + ciphertext.length)
              .put((byte) id.length)
              .put(id)
              .put(ciphertext)
              .array());
    });
  }

  @Override
  public Future<byte[]> decrypt(byte[] ciphertext, String context) {
    int length = ciphertext.length == 0 ? 0 : Byte.toUnsignedInt(ciphertext[0]);
    if (length == 0 || ciphertext.length <= 1 + length) {
      return Future.failedFuture(unreadable());
    }
    String id = new String(ciphertext, 1, length, StandardCharsets.US_ASCII);
    byte[] body = Arrays.copyOfRange(ciphertext, 1 + length, ciphertext.length);
    return lifecycle.settled().compose(state -> {
      VersionHandle version = state.version(id).orElseThrow(this::unreadable);
      if (!version.state().live()) {
        throw new FailedPreconditionException(Reason.VERSION.name(),
            "The version " + id + " of " + key.name() + " that made the ciphertext is " + version.state());
      }
      return version.decrypt(body, bound(context));
    });
  }

  private byte[] bound(String context) {
    return (key.name() + '\0' + context).getBytes(StandardCharsets.UTF_8);
  }

  private FailedPreconditionException unreadable() {
    return new FailedPreconditionException(Reason.UNREADABLE.name(),
        "The ciphertext was not made by the key " + key.name() + " under that context, or was changed");
  }
}
