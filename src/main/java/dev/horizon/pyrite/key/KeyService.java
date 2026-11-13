package dev.horizon.pyrite.key;

import static dev.horizon.basepoint.expression.Expression.now;

import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.util.Base64URL;
import dev.horizon.basepoint.expression.Expression;
import dev.horizon.cinder.Database;
import dev.horizon.cinder.DuplicateKeyException;
import dev.horizon.pyrite.KeyOptions;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.crypto.v1.Algorithm;
import dev.horizon.pyrite.crypto.v1.DecryptResponse;
import dev.horizon.pyrite.crypto.v1.EncryptResponse;
import dev.horizon.pyrite.crypto.v1.Expected;
import dev.horizon.pyrite.crypto.v1.Rejected;
import dev.horizon.pyrite.crypto.v1.SignResponse;
import dev.horizon.pyrite.crypto.v1.Verified;
import dev.horizon.pyrite.crypto.v1.VerifyResponse;
import dev.horizon.pyrite.declaration.ResourceType;
import dev.horizon.pyrite.resource.Resource;
import dev.horizon.pyrite.resource.ResourceTree;
import dev.horizon.pyrite.resource.Subject;
import dev.horizon.pyrite.schema.keys.v1.Tables;
import dev.horizon.pyrite.storage.DatabaseTime;
import dev.horizon.pyrite.storage.KeyBackend;
import dev.horizon.pyrite.storage.KeyVersionBackend;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.GrpcStatus;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The keys of a declaration with {@code pyrite.dev/Key}, and their versions: it creates, finds, rotates and changes
 * them, and encrypts, decrypts, signs and verifies with them.
 *
 * <p>
 * A key's primary version encrypts and signs; every enabled version decrypts and verifies. A key with a rotation period
 * rotates at its first encryption or signature after its next rotation time, on the clock of the database.
 *
 * <p>
 * A ciphertext names the version that made it, and is bound to the key's name and the caller's associated data. It
 * decrypts only with the same key and the same associated data. A signature that does not verify is a rejection in the
 * response, not a failure. A JWT's {@code alg} is the key's algorithm, such as {@code HS256} for
 * {@code HMAC_SHA256}, and its key id is the id of the version that signed, such as {@code oauth-1}.
 */
public final class KeyService {

  /**
   * The shortest rotation period other than zero.
   */
  static final Duration MIN_ROTATION_PERIOD = Duration.ofMinutes(1);

  /**
   * The longest rotation period, 50 years: the next rotation time is now plus the period in seconds, which some
   * databases take as a 32-bit number, so a longer period would overflow.
   */
  static final Duration MAX_ROTATION_PERIOD = Duration.ofDays(18_262);

  /**
   * How many times a new version takes the next number again, when another call took the same number first.
   */
  private static final int NUMBER_ATTEMPTS = 3;

  private final ResourceTree resources;
  private final ResourceType keyType;
  private final Vertx vertx;
  private final KeyOptions options;
  private final KeyBackend keys;
  private final KeyVersionBackend versions;
  private final RootKey root;
  private final MaterialCache materials;

  /**
   * Creates the service of a declaration's keys.
   *
   * @param keyType the type {@code pyrite.dev/Key} of the declaration
   * @throws PyriteException with {@code FAILED_PRECONDITION} when the options name no root key
   */
  public KeyService(ResourceTree resources, ResourceType keyType, Database database, Vertx vertx,
      KeyOptions options) {
    this.resources = resources;
    this.keyType = keyType;
    this.vertx = vertx;
    this.options = options;
    this.keys = new KeyBackend(database);
    this.versions = new KeyVersionBackend(database);
    this.root = new RootKey(Optional.ofNullable(options.getRoot()).orElseThrow(() -> new PyriteException(
        GrpcStatus.FAILED_PRECONDITION, "The declaration has keys, whose material a root key seals, but the options"
            + " name no root key.")));
    this.materials = new MaterialCache(root);
  }

  /**
   * Creates a key under a parent, with its first version, with no values for the conditions of grants.
   *
   * @see #create(Subject, String, String, Algorithm, Duration, Struct)
   */
  public Future<Tables.Key> create(Subject creator, String id, String parent, Algorithm algorithm, Duration period) {
    return create(creator, id, parent, algorithm, period, Struct.getDefaultInstance());
  }

  /**
   * Creates a key under a parent, with its first version. When a step after the resource fails, the resource is
   * forgotten again, and the key's rows go with it.
   *
   * @param creator the subject, which needs the type's create permission on the parent, and gets the type's creator
   * relation on the key
   * @param id the key's id, the last segment of its name
   * @param parent the parent's name; empty for a key at the top
   * @param period the rotation period: zero for none, or whole seconds from a minute to 50 years
   * @param context the values that the conditions of grants read
   * @return the key, or a failure with {@code INVALID_ARGUMENT} for no algorithm, a period out of that range or an id
   * that is not valid, {@code NOT_FOUND} for a parent that is not known, {@code PERMISSION_DENIED} without the create
   * permission, and {@code ALREADY_EXISTS} for a name that is known
   */
  public Future<Tables.Key> create(Subject creator, String id, String parent, Algorithm algorithm, Duration period,
      Struct context) {
    Optional<KeyAlgorithm> keyAlgorithm = KeyAlgorithm.find(algorithm);
    if (keyAlgorithm.isEmpty()) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The request names no algorithm,"
          + " one of " + Arrays.toString(KeyAlgorithm.values()) + "."));
    }
    return checkPeriod(period).compose(ignored -> insert(creator, id, parent, keyAlgorithm.get(), period, context));
  }

  /**
   * Finds a key.
   *
   * @param name the name, such as {@code keys/payments}
   * @return the key, or a failure with {@code INVALID_ARGUMENT} for a name that is not {@code keys/{key}}, and with
   * {@code NOT_FOUND} for a key that does not exist
   */
  public Future<Tables.Key> key(String name) {
    if (!name.startsWith(keyType.plural() + "/")) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The name " + name + " is not a"
          + " key's, keys/{key}."));
    }
    return keys.findByName(name).compose(row -> row.isPresent() ? Future.succeededFuture(row.get())
        : Future.failedFuture(new PyriteException(GrpcStatus.NOT_FOUND, "The key " + name + " does not exist.")));
  }

  /**
   * Finds a key that encrypts.
   *
   * @return the key, or a failure with {@code FAILED_PRECONDITION} for a key that signs, and the failures of
   * {@link #key(String)}
   */
  public Future<Tables.Key> encryption(String name) {
    return key(name).compose(key -> key.getPurpose() == Tables.Key.Purpose.ENCRYPTION ? Future.succeededFuture(key)
        : Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The key " + name + " signs; it"
            + " does not encrypt.")));
  }

  /**
   * Finds a key that signs, of the purpose {@code MAC} or {@code SIGNING}.
   *
   * @return the key, or a failure with {@code FAILED_PRECONDITION} for a key that encrypts, and the failures of
   * {@link #key(String)}
   */
  public Future<Tables.Key> signing(String name) {
    return key(name).compose(key -> key.getPurpose() != Tables.Key.Purpose.ENCRYPTION ? Future.succeededFuture(key)
        : Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The key " + name + " encrypts;"
            + " it does not sign.")));
  }

  /**
   * Gives a key as a resource, on which a call checks the relation of its subject.
   *
   * @param name the key's name, such as {@code keys/payments}
   * @return the resource of the name
   */
  public Resource resource(String name) {
    return new Resource(resources, keyType, name);
  }

  /**
   * Makes a new version of a key and makes it the primary one. A key with a rotation period rotates next a period
   * after this rotation.
   *
   * @return the key, with its new primary version, or a failure with {@code ABORTED} when another change of the key
   * came first, which deletes the new version again, and the failures of {@link #key(String)}
   */
  public Future<Tables.Key> rotate(String name) {
    return key(name).compose(this::rotate);
  }

  /**
   * Changes how often a key rotates on its own. The next rotation is a period after the change.
   *
   * @param period the period: zero for never, or whole seconds from a minute to 50 years
   * @param revision the revision that the change is made from
   * @return the changed key, or a failure with {@code INVALID_ARGUMENT} for a period out of that range, with
   * {@code ABORTED} when the key changed since the revision, and the failures of {@link #key(String)}
   */
  public Future<Tables.Key> update(String name, Duration period, long revision) {
    return checkPeriod(period).compose(ignored -> key(name)).compose(key -> {
      if (key.getRevision() != revision) {
        return Future.failedFuture(new PyriteException(GrpcStatus.ABORTED, "The key " + name + " is at revision "
            + key.getRevision() + ", not " + revision + "."));
      }
      return save(key, key.toBuilder().setRotationPeriod(period.toSeconds()).clearNextRotationTime(), period);
    });
  }

  /**
   * Gives the versions of a key.
   *
   * @param key the key's name
   * @return the versions, oldest first
   */
  public Future<List<Tables.KeyVersion>> versions(String key) {
    return versions.children(key).map(rows -> rows.stream().sorted(Comparator.comparingLong(
        Tables.KeyVersion::getNumber)).toList());
  }

  /**
   * Finds a version of a key.
   *
   * @param name the name, such as {@code keyVersions/payments-2}
   * @return the version, or a failure with {@code NOT_FOUND} for a version that does not exist or was destroyed
   */
  public Future<Tables.KeyVersion> version(String name) {
    return versions.findByName(name).compose(row -> row.isPresent() ? Future.succeededFuture(row.get())
        : Future.failedFuture(new PyriteException(GrpcStatus.NOT_FOUND, "The key version " + name + " does not"
            + " exist.")));
  }

  /**
   * Enables or disables a version. A disabled version neither decrypts nor verifies; the primary version is not
   * disabled.
   *
   * @param version the version, as it was read
   * @param enable whether the version decrypts and verifies after the change
   * @param revision the revision that the change is made from
   * @return the changed version, or a failure with {@code ABORTED} when the version changed since the revision, and
   * with {@code FAILED_PRECONDITION} when it disables the primary version
   */
  public Future<Tables.KeyVersion> updateVersion(Tables.KeyVersion version, boolean enable, long revision) {
    if (version.getRevision() != revision) {
      return Future.failedFuture(new PyriteException(GrpcStatus.ABORTED, "The key version " + version.getName()
          + " is at revision " + version.getRevision() + ", not " + revision + "."));
    }
    Future<Void> permission = enable ? Future.succeededFuture() : requireSecondary(version);
    return permission.compose(ignored -> versions.update(version.toBuilder().setState(enable
        ? Tables.KeyVersion.State.ENABLED
        : Tables.KeyVersion.State.DISABLED).build(), version.getUid(), version.getRevision(), version.getName()));
  }

  /**
   * Destroys a version: deletes its row, with its sealed material. Nothing that it encrypted decrypts again.
   *
   * @param version the version, as it was read
   * @return the deletion, or a failure with {@code FAILED_PRECONDITION} for the primary version
   */
  public Future<Void> deleteVersion(Tables.KeyVersion version) {
    return requireSecondary(version).compose(ignored -> versions.deleteById(version.getUid())).mapEmpty();
  }

  /**
   * Encrypts with the primary version of an encryption key, after rotating the key when its rotation is due.
   *
   * @param key the key's name
   * @param associatedData the data that a decryption names again, such as the id of the row that stores the
   * ciphertext
   * @return the ciphertext and the version's name, or the failures of {@link #encryption(String)}
   */
  public Future<EncryptResponse> encrypt(String key, byte[] plaintext, byte[] associatedData) {
    return encryption(key).compose(row -> primary(row).map(version -> EncryptResponse.newBuilder()
        .setKeyVersion(version.getName())
        .setCiphertext(ByteString.copyFrom(new Ciphertext(KeyVersionName.of(version), materials.aes(version)
            .encrypt(plaintext, nameAndData(row, associatedData))).bytes()))
        .build()));
  }

  /**
   * Decrypts with the version of an encryption key that encrypted.
   *
   * @param key the key's name
   * @param associatedData the data that the encryption named
   * @return the plaintext and the version's name, or a failure with {@code FAILED_PRECONDITION} when the version is
   * disabled or destroyed, with {@code INVALID_ARGUMENT} when the ciphertext is not this key's for this data, and the
   * failures of {@link #encryption(String)}
   */
  public Future<DecryptResponse> decrypt(String key, byte[] ciphertext, byte[] associatedData) {
    return encryption(key).compose(row -> decrypt(row, ciphertext, associatedData));
  }

  /**
   * Signs data with the primary version of a key that signs, after rotating the key when its rotation is due.
   *
   * @param key the key's name
   * @return the signature and the version's name, or the failures of {@link #signing(String)}
   */
  public Future<SignResponse> sign(String key, byte[] data) {
    return signing(key).compose(row -> primary(row).map(version -> SignResponse.newBuilder()
        .setSignature(ByteString.copyFrom(materials.signing(version).sign(data))).setKeyVersion(version.getName())
        .build()));
  }

  /**
   * Verifies a signature with one version of a key that signs, or with each enabled version.
   *
   * @param key the key's name
   * @param version the version's name; empty to try each enabled version
   * @return the verdict: verified, with the version that made the signature, or rejected with
   * {@code UNKNOWN_VERSION} for a version that the key does not have, {@code DISABLED_VERSION} for a disabled one,
   * and {@code SIGNATURE} when no version made the signature; or the failures of {@link #signing(String)}
   */
  public Future<VerifyResponse> verify(String key, byte[] data, byte[] signature, String version) {
    return signing(key).compose(row -> verify(row, data, signature, version));
  }

  /**
   * Signs claims as a JWT in compact serialization, with the primary version of a key that signs. The header's key id
   * is the primary version's id.
   *
   * @param key the key's name
   * @param type the JWT's {@code typ}; empty for {@code JWT}
   * @return the token and the version's name, or the failures of {@link #signing(String)}
   */
  public Future<SignResponse> signJwt(String key, String type, Struct claims) {
    return signing(key).compose(row -> primary(row).map(version -> {
      JWSObject draft = new JWSObject(new JWSHeader.Builder(KeyAlgorithm.of(row.getAlgorithm()).jose())
          .type(new JOSEObjectType(type.isEmpty() ? Jwt.TYPE : type)).keyID(KeyVersionName.of(version).id()).build(),
          new Payload(ClaimSet.of(claims).json().encode()));
      String token = draft.getHeader().toBase64URL() + "." + draft.getPayload().toBase64URL() + "."
          + Base64URL.encode(materials.signing(version).sign(draft.getSigningInput()));
      return SignResponse.newBuilder().setToken(token).setKeyVersion(version.getName()).build();
    }));
  }

  /**
   * Verifies a JWT with a key that signs: its header, its signature with the version that its key id names, and its
   * claims against what is expected. A token without {@code exp} is rejected. Without a leeway, the clock skew of the
   * options is the leeway.
   *
   * @param key the key's name
   * @param expectation the type, the issuer, the audience and the leeway
   * @return the verdict: verified, with the claims, or rejected with the first reason that the token fails, checking
   * its header, then its signature, then its claims; or the failures of {@link #signing(String)}
   */
  public Future<VerifyResponse> verifyJwt(String key, String token, Expected expectation) {
    return signing(key).compose(row -> verifyJwt(row, token, expectation));
  }

  /**
   * Gives a key as the service returns it.
   *
   * @return the key, with its next rotation time when it rotates on its own
   */
  public static dev.horizon.pyrite.crypto.v1.Key message(Tables.Key row) {
    dev.horizon.pyrite.crypto.v1.Key.Builder message = dev.horizon.pyrite.crypto.v1.Key.newBuilder()
        .setName(row.getName()).setParent(row.getParent())
        .setPurpose(dev.horizon.pyrite.crypto.v1.Key.Purpose.valueOf(row.getPurpose().name()))
        .setAlgorithm(Algorithm.valueOf(row.getAlgorithm().name()))
        .setRotationPeriod(com.google.protobuf.Duration.newBuilder().setSeconds(row.getRotationPeriod()))
        .setPrimaryVersion(row.getPrimaryVersion()).setRevision(row.getRevision())
        .setCreateTime(DatabaseTime.timestamp(row.getCreateTime()))
        .setUpdateTime(DatabaseTime.timestamp(row.getUpdateTime()));
    if (row.hasNextRotationTime()) {
      message.setNextRotationTime(DatabaseTime.timestamp(row.getNextRotationTime()));
    }
    return message.build();
  }

  /**
   * Gives a version as the service returns it.
   *
   * @return the version, with its public key as a JWK for a version of the purpose {@code SIGNING}
   */
  public static dev.horizon.pyrite.crypto.v1.KeyVersion message(Tables.KeyVersion row) {
    dev.horizon.pyrite.crypto.v1.KeyVersion.Builder message = dev.horizon.pyrite.crypto.v1.KeyVersion.newBuilder()
        .setName(row.getName()).setNumber(row.getNumber())
        .setState(dev.horizon.pyrite.crypto.v1.KeyVersion.State.valueOf(row.getState().name()))
        .setAlgorithm(Algorithm.valueOf(row.getAlgorithm().name()))
        .setRevision(row.getRevision()).setCreateTime(DatabaseTime.timestamp(row.getCreateTime()));
    if (row.hasPublicKey()) {
      message.setPublicKey(new ClaimSet(new JsonObject(row.getPublicKey())).struct());
    }
    return message.build();
  }

  /**
   * Checks a rotation period: zero, or whole seconds from {@link #MIN_ROTATION_PERIOD} to
   * {@link #MAX_ROTATION_PERIOD}. A shorter period would let the calls that find a rotation due each rotate the key.
   *
   * @return nothing, or a failure with {@code INVALID_ARGUMENT} for a period out of that range
   */
  static Future<Void> checkPeriod(Duration period) {
    String problem;
    if (period.isNegative()) {
      problem = "The rotation period is negative.";
    } else if (period.getNano() != 0) {
      problem = "The rotation period is not a whole number of seconds.";
    } else if (!period.isZero() && period.compareTo(MIN_ROTATION_PERIOD) < 0) {
      problem = "The rotation period is shorter than a minute; it is zero for a key that does not rotate on its own.";
    } else if (period.compareTo(MAX_ROTATION_PERIOD) > 0) {
      problem = "The rotation period is longer than 50 years.";
    } else {
      return Future.succeededFuture();
    }
    return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, problem));
  }

  private Future<Tables.Key> insert(Subject creator, String id, String parent, KeyAlgorithm algorithm,
      Duration period, Struct context) {
    String name = keyType.resource(id);
    return resources.create(creator, name, parent, context).compose(resource -> {
      Tables.Key.Builder row = Tables.Key.newBuilder().setName(name).setPurpose(algorithm.purpose())
          .setAlgorithm(Tables.Key.Algorithm.valueOf(algorithm.name())).setRotationPeriod(period.toSeconds())
          .setPrimaryVersion(new KeyVersionName(id, 1).name());
      if (!parent.isEmpty()) {
        row.setParent(parent);
      }
      Map<String, Expression> set = period.isPositive() ? Map.of("next_rotation_time", Expression.add(now(), period))
          : Map.of();
      return keys.insert(row.build(), set).compose(key -> add(key, NUMBER_ATTEMPTS).map(key))
          .recover(failure -> resources.forget(name).transform(ignored -> Future.failedFuture(failure)));
    });
  }

  /**
   * Gives the primary version of a key, after rotating the key when its rotation is due. The database decides when it
   * is due, and which of the calls that find it due rotates it; the time of this server only spares the others the
   * asking.
   *
   * @param key the key, as it was read
   * @return the primary version, which is the new one when the key rotated
   */
  private Future<Tables.KeyVersion> primary(Tables.Key key) {
    if (!key.hasNextRotationTime() || DatabaseTime.instant(key.getNextRotationTime()).isAfter(Instant.now())) {
      return version(key.getPrimaryVersion());
    }
    return keys.claimRotation(key).compose(claim -> claim.isPresent() ? rotateClaim(key, claim.get())
        : key(key.getName())).compose(current -> version(current.getPrimaryVersion()));
  }

  /**
   * Rotates a key after this call claimed its rotation. When the rotation fails, the key takes back its next rotation
   * time from before the claim, unless another change came first, so that its next use rotates it again. When another
   * change came first, the key as that change left it stands.
   *
   * @param key the key as it was read before the claim
   * @param claim the key as the claim left it
   */
  private Future<Tables.Key> rotateClaim(Tables.Key key, Tables.Key claim) {
    return rotate(claim).recover(failure -> keys.releaseRotation(claim, key.getNextRotationTime())
        .transform(ignored -> isAborted(failure) ? key(key.getName()) : Future.<Tables.Key> failedFuture(failure)));
  }

  private Future<Tables.Key> rotate(Tables.Key key) {
    return add(key, NUMBER_ATTEMPTS).compose(version -> makePrimary(key, version));
  }

  /**
   * Makes a new version the primary one. When another change of the key came first, the version is deleted again, so
   * that no version that never was primary decrypts or verifies.
   */
  private Future<Tables.Key> makePrimary(Tables.Key key, Tables.KeyVersion version) {
    return save(key, key.toBuilder().setPrimaryVersion(version.getName()).clearNextRotationTime(),
        Duration.ofSeconds(key.getRotationPeriod())).recover(
            failure -> isAborted(failure)
                ? versions.deleteById(version.getUid()).transform(ignored -> Future.<Tables.Key> failedFuture(
                    new PyriteException(GrpcStatus.ABORTED, "The key " + key.getName() + " changed while it rotated;"
                        + " rotate it again.")))
                : Future.<Tables.Key> failedFuture(failure));
  }

  /**
   * Writes a change of a key, with its next rotation a period from the change, on the clock of the database.
   *
   * @param key the key, as it was read
   * @param period the rotation period; zero for none
   */
  private Future<Tables.Key> save(Tables.Key key, Tables.Key.Builder change, Duration period) {
    Map<String, Expression> set = period.isPositive() ? Map.of("next_rotation_time", Expression.add(now(), period))
        : Map.of();
    return keys.update(change.build(), set, key.getUid(), key.getRevision(), key.getName());
  }

  /**
   * Adds a version to a key, numbered after its last one. Two calls can take the same number; the database keeps one,
   * and the other takes the next number again, up to a number of attempts. The material goes into the cache once its
   * row is in the database.
   */
  private Future<Tables.KeyVersion> add(Tables.Key key, int attempts) {
    return versions.children(key.getName()).compose(existing -> {
      KeyVersionName name = new KeyVersionName(id(key), existing.stream().mapToLong(Tables.KeyVersion::getNumber)
          .max().orElse(0) + 1);
      KeyAlgorithm algorithm = KeyAlgorithm.of(key.getAlgorithm());
      return vertx.executeBlocking(algorithm::generate).compose(material -> {
        Tables.KeyVersion.Builder draft = Tables.KeyVersion.newBuilder().setName(name.name()).setParent(key.getName())
            .setNumber(name.number()).setState(Tables.KeyVersion.State.ENABLED).setAlgorithm(key.getAlgorithm())
            .setSealed(ByteString.copyFrom(root.seal(name, material.secret())));
        if (material instanceof Material.Pair pair) {
          draft.setPublicKey(algorithm.scheme().jwk(pair.publicKey(), name.id()).encode())
              .setPublicKeyInfo(ByteString.copyFrom(pair.publicKey().getEncoded()));
        }
        return versions.insert(draft.build()).map(row -> {
          materials.put(row, material);
          return row;
        });
      });
    }).recover(failure -> failure instanceof DuplicateKeyException && attempts > 1 ? add(key, attempts - 1)
        : Future.failedFuture(failure));
  }

  private Future<Void> requireSecondary(Tables.KeyVersion version) {
    return keys.findByName(version.getParent()).compose(key -> key.isPresent()
        && key.get().getPrimaryVersion().equals(version.getName())
            ? Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The version "
                + version.getName() + " is the primary version of its key; rotate the key first."))
            : Future.succeededFuture());
  }

  private Future<DecryptResponse> decrypt(Tables.Key key, byte[] bytes, byte[] associatedData) {
    Optional<Ciphertext> ciphertext = Ciphertext.parse(bytes);
    if (ciphertext.isEmpty()) {
      return Future.failedFuture(foreign(key));
    }
    KeyVersionName version = ciphertext.get().version();
    return versions.findByName(version.name()).compose(row -> {
      if (row.isEmpty() || !row.get().getParent().equals(key.getName())) {
        return Future.failedFuture(isDestroyed(key, version) ? new PyriteException(GrpcStatus.FAILED_PRECONDITION,
            "The version " + version + " that encrypted is destroyed.") : foreign(key));
      }
      Tables.KeyVersion encrypter = row.get();
      if (!isUsable(encrypter)) {
        return Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The version " + version
            + " that encrypted is disabled."));
      }
      return Future.succeededFuture(DecryptResponse.newBuilder().setKeyVersion(version.name()).setPlaintext(
          ByteString.copyFrom(materials.aes(encrypter).decrypt(ciphertext.get().body(), nameAndData(key,
              associatedData))))
          .build());
    });
  }

  private Future<VerifyResponse> verify(Tables.Key key, byte[] data, byte[] signature, String version) {
    return versions(key.getName()).map(all -> {
      List<Tables.KeyVersion> candidates = version.isEmpty() ? all.stream().filter(KeyService::isUsable).toList()
          : all.stream().filter(candidate -> candidate.getName().equals(version)).toList();
      Rejected.Builder rejection = Rejected.newBuilder();
      if (!version.isEmpty() && candidates.isEmpty()) {
        rejection.setReason(Rejected.Reason.UNKNOWN_VERSION).setMessage("The key has no version " + version + ".");
      } else if (!version.isEmpty() && !isUsable(candidates.getFirst())) {
        rejection.setReason(Rejected.Reason.DISABLED_VERSION).setMessage("The version " + version + " is disabled.");
      } else {
        for (Tables.KeyVersion candidate : candidates) {
          if (materials.signing(candidate).verifies(data, signature)) {
            return VerifyResponse.newBuilder().setVerified(Verified.newBuilder().setKeyVersion(candidate.getName()))
                .build();
          }
        }
        rejection.setReason(Rejected.Reason.SIGNATURE).setMessage("No version of the key made the signature.");
      }
      return VerifyResponse.newBuilder().setRejected(rejection).build();
    });
  }

  private Future<VerifyResponse> verifyJwt(Tables.Key key, String token, Expected expectation) {
    Optional<Jwt> jws = Jwt.parse(token);
    if (jws.isEmpty()) {
      return Future.succeededFuture(VerifyResponse.newBuilder().setRejected(Rejected.newBuilder()
          .setReason(Rejected.Reason.MALFORMED).setMessage("The token is not a JWS in compact serialization."))
          .build());
    }
    Jwt jwt = jws.get();
    Optional<Rejected> header = jwt.header(KeyAlgorithm.of(key.getAlgorithm()).jose(),
        expectation.getType().isEmpty() ? Jwt.TYPE : expectation.getType());
    if (header.isPresent()) {
      return Future.succeededFuture(VerifyResponse.newBuilder().setRejected(header.get()).build());
    }
    String version = KeyVersionName.PREFIX + jwt.keyId();
    Expected leeway = expectation.hasLeeway() ? expectation
        : expectation.toBuilder().setLeeway(com.google.protobuf.Duration
            .newBuilder().setSeconds(options.getClockSkew().toSeconds())).build();
    return verify(key, jwt.signingInput(), jwt.signature(), version).map(verdict -> verdict.hasRejected() ? verdict
        : jwt.claims(leeway, Instant.now(), version));
  }

  /**
   * Tells whether a version of a key that no row holds was destroyed: a version is numbered up to the primary one.
   */
  private static boolean isDestroyed(Tables.Key key, KeyVersionName version) {
    return version.key().equals(id(key)) && KeyVersionName.parse(key.getPrimaryVersion())
        .map(primary -> version.number() <= primary.number()).orElse(false);
  }

  /**
   * Tells whether a version decrypts and verifies, which an enabled one does.
   */
  private static boolean isUsable(Tables.KeyVersion version) {
    return version.getState() == Tables.KeyVersion.State.ENABLED;
  }

  /**
   * Gives the id of a key, which the names of its versions start with, such as {@code payments}.
   */
  private static String id(Tables.Key key) {
    return key.getName().substring(key.getName().indexOf('/') + 1);
  }

  private static PyriteException foreign(Tables.Key key) {
    return new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The ciphertext was not made by the key " + key.getName()
        + ".");
  }

  /**
   * Gives the key's name before the associated data, so that a ciphertext does not decrypt with another key.
   */
  private static byte[] nameAndData(Tables.Key key, byte[] associatedData) {
    byte[] name = (key.getName() + '\0').getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(name.length + associatedData.length).put(name).put(associatedData).array();
  }

  private static boolean isAborted(Throwable failure) {
    return failure instanceof PyriteException conflict && conflict.status() == GrpcStatus.ABORTED;
  }
}
