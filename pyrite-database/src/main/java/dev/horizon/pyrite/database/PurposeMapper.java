package dev.horizon.pyrite.database;

import dev.horizon.pyrite.Algorithm;
import dev.horizon.pyrite.Encryption;
import dev.horizon.pyrite.Mac;
import dev.horizon.pyrite.Purpose;
import dev.horizon.pyrite.Signing;
import dev.horizon.pyrite.database.schema.v1.Tables;

final class PurposeMapper implements ProtobufMapper<Purpose<?>, Tables.Key, Tables.Key.Builder> {

  private static final String ENCRYPTION = "ENCRYPTION";
  private static final String MAC = "MAC";
  private static final String SIGNING = "SIGNING";

  @Override
  public Purpose<?> read(Tables.Key row) {
    return switch (row.getPurpose()) {
      case ENCRYPTION -> new Encryption();
      case MAC -> new Mac();
      case SIGNING -> new Signing(Algorithm.valueOf(row.getAlgorithm()));
      default -> throw new IllegalStateException("The key " + row.getName() + " has the purpose " + row.getPurpose()
          + ", which this version of Pyrite does not know");
    };
  }

  @Override
  public Tables.Key.Builder write(Tables.Key.Builder row, Purpose<?> purpose) {
    return switch (purpose) {
      case Encryption encryption -> row.setPurpose(ENCRYPTION).clearAlgorithm();
      case Mac mac -> row.setPurpose(MAC).clearAlgorithm();
      case Signing signing -> row.setPurpose(SIGNING).setAlgorithm(signing.algorithm().name());
    };
  }
}
