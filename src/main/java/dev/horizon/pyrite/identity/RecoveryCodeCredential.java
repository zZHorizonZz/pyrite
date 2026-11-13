package dev.horizon.pyrite.identity;

import com.google.protobuf.ByteString;
import dev.horizon.pyrite.authentication.v1.RecoveryCodeAnswer;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * A set of recovery codes, which an account uses once each in place of a second factor. The credential holds a JSON
 * array of the MAC of each code that is left, which the MAC key of the options signs, never the codes.
 */
public final class RecoveryCodeCredential extends Credential<RecoveryCodeAnswer> {

  static final int CODES = 10;

  private static final int CODE_BYTES = 10;
  private static final SecureRandom RANDOM = new SecureRandom();

  RecoveryCodeCredential(CredentialType type, Tables.Factor row) {
    super(type, row);
  }

  /**
   * Gives ten new random codes, each of 10 random bytes in base64url without padding.
   */
  static List<String> generate() {
    List<String> codes = new ArrayList<>();
    for (int index = 0; index < CODES; index++) {
      byte[] code = new byte[CODE_BYTES];
      RANDOM.nextBytes(code);
      codes.add(Base64.getUrlEncoder().withoutPadding().encodeToString(code));
    }
    return codes;
  }

  /**
   * Gives the data whose MAC the credential holds for a code.
   */
  static byte[] data(String code) {
    return ("recovery\0" + code.strip()).getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Verifies a code, with spaces around it or not: if one of the codes that are left is this one, the credential no
   * longer holds it, so each code works once.
   *
   * @return {@link Proof#POSSESSION} when the code was one that was left
   */
  @Override
  public Future<Set<Proof>> verify(RecoveryCodeAnswer answer, byte[] nonce) {
    String code = answer.getCode();
    JsonArray macs = new JsonArray(row().getSecret().toString(StandardCharsets.UTF_8));
    return type().macKey().compose(key -> {
      Future<Integer> match = Future.succeededFuture(-1);
      for (int index = 0; index < macs.size(); index++) {
        int at = index;
        match = match.compose(found -> found >= 0 ? Future.succeededFuture(found)
            : key.verify(data(code), Base64.getDecoder().decode(macs.getString(at)), "")
                .map(verdict -> verdict.hasVerified() ? at : -1));
      }
      return match;
    }).compose(at -> {
      if (at < 0) {
        return Future.succeededFuture(Set.<Proof> of());
      }
      macs.remove(at.intValue());
      return change(row().toBuilder().setSecret(ByteString.copyFromUtf8(macs.encode()))).map(Set.of(Proof.POSSESSION));
    });
  }

  @Override
  boolean replacesActive() {
    return true;
  }

  @Override
  boolean signsInAlone() {
    return false;
  }
}
