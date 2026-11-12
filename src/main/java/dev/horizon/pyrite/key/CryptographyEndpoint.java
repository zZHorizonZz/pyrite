package dev.horizon.pyrite.key;

import com.google.protobuf.Empty;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.crypto.v1.CreateKeyRequest;
import dev.horizon.pyrite.crypto.v1.CryptographyGrpcService;
import dev.horizon.pyrite.crypto.v1.DecryptRequest;
import dev.horizon.pyrite.crypto.v1.DecryptResponse;
import dev.horizon.pyrite.crypto.v1.DeleteKeyVersionRequest;
import dev.horizon.pyrite.crypto.v1.EncryptRequest;
import dev.horizon.pyrite.crypto.v1.EncryptResponse;
import dev.horizon.pyrite.crypto.v1.GetKeyRequest;
import dev.horizon.pyrite.crypto.v1.ListKeyVersionsRequest;
import dev.horizon.pyrite.crypto.v1.ListKeyVersionsResponse;
import dev.horizon.pyrite.crypto.v1.RotateKeyRequest;
import dev.horizon.pyrite.crypto.v1.SignRequest;
import dev.horizon.pyrite.crypto.v1.SignResponse;
import dev.horizon.pyrite.crypto.v1.UpdateKeyRequest;
import dev.horizon.pyrite.crypto.v1.UpdateKeyVersionRequest;
import dev.horizon.pyrite.crypto.v1.VerifyRequest;
import dev.horizon.pyrite.crypto.v1.VerifyResponse;
import dev.horizon.pyrite.deployment.Deployment;
import dev.horizon.pyrite.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;
import java.util.Arrays;

/**
 * The service {@code pyrite.crypto.v1.Cryptography}: keys and their versions, and encrypting, decrypting, signing and
 * verifying with them.
 *
 * <p>
 * Each call reads the latest declaration, finds the key, and checks the relation of the call on it for the subject of
 * the request: {@code viewer} to read a key, {@code admin} to change it or its versions, and {@code encrypter},
 * {@code decrypter}, {@code signer} or {@code verifier} to use it. A call ends with the status of its
 * {@link PyriteException}, such as {@code FAILED_PRECONDITION} when the declaration has no {@code pyrite.dev/Key}.
 */
public final class CryptographyEndpoint extends CryptographyGrpcService {

  private final Deployment deployment;

  public CryptographyEndpoint(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  public Future<dev.horizon.pyrite.crypto.v1.Key> createKey(CreateKeyRequest request) {
    return deployment.schema().compose(schema -> {
      Algorithm algorithm = Arrays.stream(Algorithm.values())
          .filter(known -> known.name().equals(request.getAlgorithm().name())).findFirst()
          .orElseThrow(() -> new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The request names no algorithm, one"
              + " of " + Arrays.toString(Algorithm.values()) + "."));
      return schema.keys().create(Subject.parse(request.getSubject()), request.getKeyId(), request.getParent(),
          algorithm, duration(request.getRotationPeriod()), request.getContext());
    }).map(Key::message).recover(PyriteException::fail);
  }

  @Override
  public Future<dev.horizon.pyrite.crypto.v1.Key> getKey(GetKeyRequest request) {
    return deployment.schema().compose(schema -> schema.keys().key(request.getName()))
        .compose(key -> key.resource().require(Subject.parse(request.getSubject()), "viewer", request.getContext())
            .map(key))
        .map(Key::message).recover(PyriteException::fail);
  }

  @Override
  public Future<dev.horizon.pyrite.crypto.v1.Key> updateKey(UpdateKeyRequest request) {
    return deployment.schema().compose(schema -> schema.keys().key(request.getName()))
        .compose(key -> key.resource().require(Subject.parse(request.getSubject()), "admin", request.getContext())
            .map(key))
        .compose(key -> key.update(duration(request.getRotationPeriod()), request.getRevision()))
        .map(Key::message).recover(PyriteException::fail);
  }

  @Override
  public Future<dev.horizon.pyrite.crypto.v1.Key> rotateKey(RotateKeyRequest request) {
    return deployment.schema().compose(schema -> schema.keys().key(request.getName()))
        .compose(key -> key.resource().require(Subject.parse(request.getSubject()), "admin", request.getContext())
            .map(key))
        .compose(Key::rotate).map(Key::message).recover(PyriteException::fail);
  }

  @Override
  public Future<ListKeyVersionsResponse> listKeyVersions(ListKeyVersionsRequest request) {
    return deployment.schema().compose(schema -> schema.keys().key(request.getKey()))
        .compose(key -> key.resource().require(Subject.parse(request.getSubject()), "viewer", request.getContext())
            .map(key))
        .compose(Key::versions)
        .map(versions -> ListKeyVersionsResponse.newBuilder()
            .addAllVersions(versions.stream().map(KeyVersion::message).toList()).build())
        .recover(PyriteException::fail);
  }

  @Override
  public Future<dev.horizon.pyrite.crypto.v1.KeyVersion> updateKeyVersion(UpdateKeyVersionRequest request) {
    return deployment.schema().compose(schema -> {
      boolean enabled = switch (request.getState()) {
        case ENABLED -> true;
        case DISABLED -> false;
        case STATE_UNSPECIFIED, UNRECOGNIZED -> throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "A version"
            + " is ENABLED or DISABLED.");
      };
      return schema.keys().version(request.getName())
          .compose(version -> version.key()
              .compose(key -> key.resource().require(Subject.parse(request.getSubject()), "admin",
                  request.getContext()))
              .compose(allowed -> version.update(enabled, request.getRevision())));
    }).map(KeyVersion::message).recover(PyriteException::fail);
  }

  @Override
  public Future<Empty> deleteKeyVersion(DeleteKeyVersionRequest request) {
    return deployment.schema().compose(schema -> schema.keys().version(request.getName()))
        .compose(version -> version.key()
            .compose(key -> key.resource().require(Subject.parse(request.getSubject()), "admin",
                request.getContext()))
            .compose(allowed -> version.delete()))
        .map(Empty.getDefaultInstance()).recover(PyriteException::fail);
  }

  @Override
  public Future<EncryptResponse> encrypt(EncryptRequest request) {
    return deployment.schema().compose(schema -> schema.keys().encryption(request.getKey()))
        .compose(key -> key.resource().require(Subject.parse(request.getSubject()), "encrypter",
            request.getContext()).map(key))
        .compose(key -> key.encrypt(request.getPlaintext().toByteArray(), request.getAssociatedData().toByteArray()))
        .recover(PyriteException::fail);
  }

  @Override
  public Future<DecryptResponse> decrypt(DecryptRequest request) {
    return deployment.schema().compose(schema -> schema.keys().encryption(request.getKey()))
        .compose(key -> key.resource().require(Subject.parse(request.getSubject()), "decrypter",
            request.getContext()).map(key))
        .compose(key -> key.decrypt(request.getCiphertext().toByteArray(),
            request.getAssociatedData().toByteArray()))
        .recover(PyriteException::fail);
  }

  @Override
  public Future<SignResponse> sign(SignRequest request) {
    return deployment.schema().compose(schema -> {
      if (request.getPayloadCase() == SignRequest.PayloadCase.PAYLOAD_NOT_SET) {
        throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The request names neither data nor claims to sign.");
      }
      return schema.keys().signing(request.getKey());
    }).compose(key -> key.resource().require(Subject.parse(request.getSubject()), "signer", request.getContext())
        .map(key))
        .compose(key -> request.hasClaims() ? key.jwt(request.getType(), request.getClaims())
            : key.sign(request.getData().toByteArray()))
        .recover(PyriteException::fail);
  }

  @Override
  public Future<VerifyResponse> verify(VerifyRequest request) {
    return deployment.schema().compose(schema -> {
      if (request.getPayloadCase() == VerifyRequest.PayloadCase.PAYLOAD_NOT_SET) {
        throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The request names neither a signature nor a token"
            + " to verify.");
      }
      return schema.keys().signing(request.getKey());
    }).compose(key -> key.resource().require(Subject.parse(request.getSubject()), "verifier", request.getContext())
        .map(key))
        .compose(key -> request.hasDetached()
            ? key.verify(request.getDetached().getData().toByteArray(),
                request.getDetached().getSignature().toByteArray(), request.getDetached().getKeyVersion())
            : key.verify(request.getToken(), request.getExpected()))
        .recover(PyriteException::fail);
  }

  private static Duration duration(com.google.protobuf.Duration duration) {
    return Duration.ofSeconds(duration.getSeconds(), duration.getNanos());
  }
}
