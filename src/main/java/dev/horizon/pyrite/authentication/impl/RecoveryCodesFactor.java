package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.impl.storage.Row;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class RecoveryCodesFactor extends FactorImpl implements Factor {

  private final Optional<List<String>> codes;

  RecoveryCodesFactor(FactorTypeImpl type, Row row) {
    this(type, row, Optional.empty());
  }

  private RecoveryCodesFactor(FactorTypeImpl type, Row row, Optional<List<String>> codes) {
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
    return type().accounts().macKey().compose(key -> {
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
      return change(Map.of(FactorType.SECRET, macs.encode().getBytes(StandardCharsets.UTF_8))).map(true);
    });
  }

  RecoveryCodesFactor withCodes(List<String> codes) {
    return new RecoveryCodesFactor(type(), row(), Optional.of(List.copyOf(codes)));
  }
}
