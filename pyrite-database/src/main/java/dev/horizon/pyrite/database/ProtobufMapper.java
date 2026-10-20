package dev.horizon.pyrite.database;

import com.google.protobuf.Message;

/**
 * Converts one value of the API to the columns of a protobuf row, and back. Only the mapper knows how the value is
 * stored.
 *
 * @param <T> the value of the api
 * @param <M> the record
 * @param <B> the builder of the record
 */
interface ProtobufMapper<T, M extends Message, B extends Message.Builder> {

  /**
   * Reads the value from the columns of the row.
   *
   * @throws IllegalStateException if the columns hold a value that this version of Pyrite does not know
   */
  T read(M row);

  /**
   * Writes the value to the columns of the row, and returns the row.
   */
  B write(B row, T value);
}
