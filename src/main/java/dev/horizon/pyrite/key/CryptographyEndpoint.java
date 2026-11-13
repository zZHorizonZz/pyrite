package dev.horizon.pyrite.key;

import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
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
import dev.horizon.pyrite.schema.keys.v1.Tables;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;

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
    return deployment.schema().compose(schema -> schema.keys().create(Subject.parse(request.getSubject()),
        request.getKeyId(), request.getParent(), request.getAlgorithm(), duration(request.getRotationPeriod()),
        request.getContext()))
        .map(KeyService::message).recover(PyriteException::fail);
  }

  @Override
  public Future<dev.horizon.pyrite.crypto.v1.Key> getKey(GetKeyRequest request) {
    return deployment.schema().compose(schema -> {
      KeyService keys = schema.keys();
      return keys.key(request.getName())
          .compose(key -> require(keys, key, request.getSubject(), "viewer", request.getContext()));
    }).map(KeyService::message).recover(PyriteException::fail);
  }

  @Override
  public Future<dev.horizon.pyrite.crypto.v1.Key> updateKey(UpdateKeyRequest request) {
    return deployment.schema().compose(schema -> {
      KeyService keys = schema.keys();
      return keys.key(request.getName())
          .compose(key -> require(keys, key, request.getSubject(), "admin", request.getContext()))
          .compose(key -> keys.update(key.getName(), duration(request.getRotationPeriod()), request.getRevision()));
    }).map(KeyService::message).recover(PyriteException::fail);
  }

  @Override
  public Future<dev.horizon.pyrite.crypto.v1.Key> rotateKey(RotateKeyRequest request) {
    return deployment.schema().compose(schema -> {
      KeyService keys = schema.keys();
      return keys.key(request.getName())
          .compose(key -> require(keys, key, request.getSubject(), "admin", request.getContext()))
          .compose(key -> keys.rotate(key.getName()));
    }).map(KeyService::message).recover(PyriteException::fail);
  }

  @Override
  public Future<ListKeyVersionsResponse> listKeyVersions(ListKeyVersionsRequest request) {
    return deployment.schema().compose(schema -> {
      KeyService keys = schema.keys();
      return keys.key(request.getKey())
          .compose(key -> require(keys, key, request.getSubject(), "viewer", request.getContext()))
          .compose(key -> keys.versions(key.getName()));
    }).map(versions -> ListKeyVersionsResponse.newBuilder()
        .addAllVersions(versions.stream().map(KeyService::message).toList()).build())
        .recover(PyriteException::fail);
  }

  @Override
  public Future<dev.horizon.pyrite.crypto.v1.KeyVersion> updateKeyVersion(UpdateKeyVersionRequest request) {
    return deployment.schema().compose(schema -> {
      boolean enable = switch (request.getState()) {
        case ENABLED -> true;
        case DISABLED -> false;
        case STATE_UNSPECIFIED, UNRECOGNIZED -> throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "A version"
            + " is ENABLED or DISABLED.");
      };
      KeyService keys = schema.keys();
      return keys.version(request.getName())
          .compose(version -> keys.key(version.getParent())
              .compose(key -> require(keys, key, request.getSubject(), "admin", request.getContext()))
              .compose(permission -> keys.updateVersion(version, enable, request.getRevision())));
    }).map(KeyService::message).recover(PyriteException::fail);
  }

  @Override
  public Future<Empty> deleteKeyVersion(DeleteKeyVersionRequest request) {
    return deployment.schema().compose(schema -> {
      KeyService keys = schema.keys();
      return keys.version(request.getName())
          .compose(version -> keys.key(version.getParent())
              .compose(key -> require(keys, key, request.getSubject(), "admin", request.getContext()))
              .compose(permission -> keys.deleteVersion(version)));
    }).map(Empty.getDefaultInstance()).recover(PyriteException::fail);
  }

  @Override
  public Future<EncryptResponse> encrypt(EncryptRequest request) {
    return deployment.schema().compose(schema -> {
      KeyService keys = schema.keys();
      return keys.encryption(request.getKey())
          .compose(key -> require(keys, key, request.getSubject(), "encrypter", request.getContext()))
          .compose(key -> keys.encrypt(key.getName(), request.getPlaintext().toByteArray(),
              request.getAssociatedData().toByteArray()));
    }).recover(PyriteException::fail);
  }

  @Override
  public Future<DecryptResponse> decrypt(DecryptRequest request) {
    return deployment.schema().compose(schema -> {
      KeyService keys = schema.keys();
      return keys.encryption(request.getKey())
          .compose(key -> require(keys, key, request.getSubject(), "decrypter", request.getContext()))
          .compose(key -> keys.decrypt(key.getName(), request.getCiphertext().toByteArray(),
              request.getAssociatedData().toByteArray()));
    }).recover(PyriteException::fail);
  }

  @Override
  public Future<SignResponse> sign(SignRequest request) {
    return deployment.schema().compose(schema -> {
      if (request.getPayloadCase() == SignRequest.PayloadCase.PAYLOAD_NOT_SET) {
        throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The request names neither data nor claims to sign.");
      }
      KeyService keys = schema.keys();
      return keys.signing(request.getKey())
          .compose(key -> require(keys, key, request.getSubject(), "signer", request.getContext()))
          .compose(key -> request.hasClaims() ? keys.signJwt(key.getName(), request.getType(), request.getClaims())
              : keys.sign(key.getName(), request.getData().toByteArray()));
    }).recover(PyriteException::fail);
  }

  @Override
  public Future<VerifyResponse> verify(VerifyRequest request) {
    return deployment.schema().compose(schema -> {
      if (request.getPayloadCase() == VerifyRequest.PayloadCase.PAYLOAD_NOT_SET) {
        throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The request names neither a signature nor a token"
            + " to verify.");
      }
      KeyService keys = schema.keys();
      return keys.signing(request.getKey())
          .compose(key -> require(keys, key, request.getSubject(), "verifier", request.getContext()))
          .compose(key -> request.hasDetached()
              ? keys.verify(key.getName(), request.getDetached().getData().toByteArray(),
                  request.getDetached().getSignature().toByteArray(), request.getDetached().getKeyVersion())
              : keys.verifyJwt(key.getName(), request.getToken(), request.getExpected()));
    }).recover(PyriteException::fail);
  }

  /**
   * Gives a key, if the subject of a request has a relation on it.
   *
   * @return the key, or a failure with {@code INVALID_ARGUMENT} for a subject of no form, and with
   * {@code PERMISSION_DENIED} without the relation
   */
  private static Future<Tables.Key> require(KeyService keys, Tables.Key key, String subject, String relation,
      Struct context) {
    return keys.resource(key.getName()).require(Subject.parse(subject), relation, context).map(key);
  }

  private static Duration duration(com.google.protobuf.Duration duration) {
    return Duration.ofSeconds(duration.getSeconds(), duration.getNanos());
  }
}
