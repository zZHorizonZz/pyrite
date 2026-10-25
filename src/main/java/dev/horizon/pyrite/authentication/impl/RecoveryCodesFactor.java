package dev.horizon.pyrite.authentication.impl;

import com.google.protobuf.ByteString;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

final class RecoveryCodesFactor extends FactorImpl implements Factor {

  private final Optional<List<String>> codes;

  RecoveryCodesFactor(AccountTypeImpl type, Tables.Factor row) {
    this(type, row, Optional.empty());
  }

  private RecoveryCodesFactor(AccountTypeImpl type, Tables.Factor row, Optional<List<String>> codes) {
    super(type, row);
    this.codes = codes;
  }

  static byte[] data(String code) {
    return ("recovery\0" + code).getBytes(StandardCharsets.UTF_8);
  }

  public Optional<List<String>> codes() {
    return codes;
  }

  public Future<Boolean> use(String code) {
    JsonArray macs = new JsonArray(new String(secret(), StandardCharsets.UTF_8));
    return type().macKey().compose(key -> {
      Future<Integer> matched = Future.succeededFuture(-1);
      for (int index = 0; index < macs.size(); index++) {
        int at = index;
        matched = matched.compose(match -> match >= 0 ? Future.succeededFuture(match)
            : key.verify(data(code.strip()), Base64.getDecoder().decode(macs.getString(at)), "")
                .map(verdict -> verdict.hasVerified() ? at : -1));
      }
      return matched;
    }).compose(match -> {
      if (match < 0) {
        return Future.succeededFuture(false);
      }
      macs.remove(match.intValue());
      return change(row().toBuilder().setSecret(ByteString.copyFromUtf8(macs.encode()))).map(true);
    });
  }

  RecoveryCodesFactor withCodes(List<String> codes) {
    return new RecoveryCodesFactor(type(), row(), Optional.of(List.copyOf(codes)));
  }
}
