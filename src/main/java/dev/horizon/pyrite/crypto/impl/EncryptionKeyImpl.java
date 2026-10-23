package dev.horizon.pyrite.crypto.impl;

import com.google.protobuf.ByteString;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.crypto.v1.DecryptResponse;
import dev.horizon.pyrite.crypto.v1.EncryptResponse;
import dev.horizon.pyrite.impl.storage.Row;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

final class EncryptionKeyImpl extends KeyImpl implements EncryptionKey {

  EncryptionKeyImpl(KeyTypeImpl type, Row row) {
    super(type, row);
  }

  @Override
  public Future<EncryptResponse> encrypt(byte[] plaintext, byte[] associatedData) {
    return primary().map(version -> {
      byte[] id = version.id().getBytes(StandardCharsets.US_ASCII);
      byte[] body = ((KeyVersionImpl) version).cipher().encrypt(plaintext, bound(associatedData));
      return EncryptResponse.newBuilder().setKeyVersion(version.name()).setCiphertext(ByteString.copyFrom(ByteBuffer
          .allocate(1 + id.length + body.length).put((byte) id.length).put(id).put(body).array())).build();
    });
  }

  @Override
  public Future<DecryptResponse> decrypt(byte[] ciphertext, byte[] associatedData) {
    int length = ciphertext.length == 0 ? 0 : Byte.toUnsignedInt(ciphertext[0]);
    if (length == 0 || ciphertext.length <= 1 + length) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The ciphertext was not made by the key " + name()
          + ".");
    }
    String name = type().versions().declaration().resource(new String(ciphertext, 1, length,
        StandardCharsets.US_ASCII));
    return type()
        .versions().get(name).map(found -> found.filter(candidate -> candidate.parentName().equals(Optional.of(name())))
            .orElseThrow(() -> new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The ciphertext was not made by the key "
                + name() + ".")))
        .map(version -> {
          if (!version.isUsable()) {
            throw new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The version " + name + " that encrypted is"
                + " disabled or deleted.");
          }
          byte[] plaintext = ((KeyVersionImpl) version).cipher().decrypt(Arrays.copyOfRange(ciphertext, 1 + length,
              ciphertext.length), bound(associatedData));
          return DecryptResponse.newBuilder().setPlaintext(ByteString.copyFrom(plaintext)).setKeyVersion(name).build();
        });
  }

  private byte[] bound(byte[] associatedData) {
    byte[] name = (name() + '\0').getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(name.length + associatedData.length).put(name).put(associatedData).array();
  }
}
