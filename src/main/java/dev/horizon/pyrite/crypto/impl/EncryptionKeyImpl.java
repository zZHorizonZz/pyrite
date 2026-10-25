package dev.horizon.pyrite.crypto.impl;

import com.google.protobuf.ByteString;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.crypto.v1.DecryptResponse;
import dev.horizon.pyrite.crypto.v1.EncryptResponse;
import dev.horizon.pyrite.schema.keys.v1.Tables;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Pattern;

final class EncryptionKeyImpl extends KeyImpl implements EncryptionKey {

  EncryptionKeyImpl(KeyTypeImpl type, Tables.Key row) {
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
    String name = KeyTypeImpl.VERSIONS + new String(ciphertext, 1, length, StandardCharsets.US_ASCII);
    return type().versions().findByName(name)
        .map(found -> found.filter(candidate -> candidate.getParent().equals(name()))
            .map(row -> new KeyVersionImpl(type(), row))
            .orElseThrow(() -> destroyed(name)
                ? new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The version " + name + " that encrypted is"
                    + " destroyed.")
                : new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The ciphertext was not made by the key " + name()
                    + ".")))
        .map(version -> {
          if (!version.isUsable()) {
            throw new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The version " + name + " that encrypted is"
                + " disabled or deleted.");
          }
          byte[] plaintext = version.cipher().decrypt(Arrays.copyOfRange(ciphertext, 1 + length,
              ciphertext.length), bound(associatedData));
          return DecryptResponse.newBuilder().setPlaintext(ByteString.copyFrom(plaintext)).setKeyVersion(name).build();
        });
  }

  private boolean destroyed(String version) {
    String prefix = KeyTypeImpl.VERSIONS + id() + "-";
    if (!version.matches(Pattern.quote(prefix) + "[1-9][0-9]{0,17}")) {
      return false;
    }
    long number = Long.parseLong(version.substring(prefix.length()));
    return number <= Long.parseLong(primaryVersion().substring(prefix.length()));
  }

  private byte[] bound(byte[] associatedData) {
    byte[] name = (name() + '\0').getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(name.length + associatedData.length).put(name).put(associatedData).array();
  }
}
