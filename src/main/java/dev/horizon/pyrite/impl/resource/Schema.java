package dev.horizon.pyrite.impl.resource;

import dev.horizon.pyrite.PyriteServiceOptions;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.authentication.impl.FlowType;
import dev.horizon.pyrite.impl.Adapter;
import dev.horizon.pyrite.impl.model.TypeSystem;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.time.Clock;
import java.util.Optional;

/**
 * A declaration bound to its tables and its OpenFGA store: the resource types of the declaration, by name.
 *
 * <p>
 * A schema is what a call works on. It finds a type by the name of the type or of a resource, and reads any resource by
 * its name.
 */
public interface Schema {

  /**
   * Gives the compiled declaration.
   *
   * @return the type system
   */
  TypeSystem types();

  /**
   * Gives the adapter of the declaration's OpenFGA store and model.
   *
   * @return the adapter
   */
  Adapter adapter();

  /**
   * Gives the Vert.x instance, which runs key generation and password hashing on worker threads.
   *
   * @return the Vert.x instance
   */
  Vertx vertx();

  /**
   * Gives the clock of every time that Pyrite stores and compares.
   *
   * @return the clock
   */
  Clock clock();

  /**
   * Gives the options of the service.
   *
   * @return the options
   */
  PyriteServiceOptions options();

  /**
   * Gives the flows of the declaration.
   *
   * @return the flows, or a failure with {@code FAILED_PRECONDITION} when the declaration has no accounts
   */
  FlowType flows();

  /**
   * Gives a declared type.
   *
   * @param name the type's name, such as {@code acme.dev/Project}
   * @return the type, or a failure with {@code INVALID_ARGUMENT} when it is not declared
   */
  ResourceType<?> type(String name);

  /**
   * Gives one of Pyrite's own types as its interface, such as {@code pyrite.dev/Key} as a {@code KeyType}.
   *
   * @param <T> the interface
   * @param name the type's name
   * @param kind the interface's class
   * @return the type
   */
  <T extends ResourceType<?>> T type(String name, Class<T> kind);

  /**
   * Reads a resource of any declared type, from the table of the type that its name's first segment names.
   *
   * @param name the resource's name, such as {@code projects/ledger}
   * @return the resource, or empty when no declared type or no row has the name
   */
  Future<Optional<Resource>> get(String name);

  /**
   * Reads a resource of any declared type.
   *
   * @param name the resource's name
   * @return the resource, or a failure with {@code NOT_FOUND}
   */
  Future<Resource> require(String name);

  /**
   * Gives an OpenFGA tuple as a grant on a resource, with the resource's name in place of the object.
   *
   * @param tuple the tuple
   * @return the grant, or empty when the tuple's object is of no declared type
   */
  Optional<Grant> grant(TupleKey tuple);

  /**
   * Purges the deleted resources of every type whose retention has ended, with their grants.
   *
   * @return the number of resources purged
   */
  Future<Long> purge();
}
