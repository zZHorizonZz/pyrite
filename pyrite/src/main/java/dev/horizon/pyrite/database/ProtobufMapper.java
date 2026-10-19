package dev.horizon.pyrite.database;

import com.google.protobuf.Message;

/**
 * Maps one value of the api to the columns that hold it in a protobuf record, and back. A mapper is the only place
 * that knows how its value is stored.
 *
 * @param <T> the value of the api
 * @param <M> the record
 * @param <B> the builder of the record
 */
interface ProtobufMapper<T, M extends Message, B extends Message.Builder> {

  /**
   * @return the value that the columns of {@code row} hold
   * @throws IllegalStateException when the columns hold a value this version of Pyrite does not know
   */
  T read(M row);

  /**
   * @return {@code row}, with {@code value} written into its columns
   */
  B write(B row, T value);
}
