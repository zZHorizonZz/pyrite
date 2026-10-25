package dev.horizon.pyrite.impl.resource;

import com.google.protobuf.Struct;
import dev.horizon.pyrite.PyriteServiceOptions;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.authentication.impl.AccountTypeImpl;
import dev.horizon.pyrite.authentication.impl.FlowType;
import dev.horizon.pyrite.crypto.impl.KeyTypeImpl;
import dev.horizon.pyrite.impl.Adapter;
import dev.horizon.pyrite.impl.model.DeclaredType;
import dev.horizon.pyrite.impl.model.TypeSystem;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.time.Clock;
import java.util.Optional;

/**
 * A declaration bound to its database and its OpenFGA store: its types, the resources that Pyrite knows, and Pyrite's
 * own keys, accounts and flows.
 *
 * <p>
 * A schema is what a call works on. The application keeps its resources itself; the schema knows each one's type and
 * parent, so that a grant on a parent reaches it.
 */
public interface Schema {

  /**
   * Gives the compiled declaration.
   *
   * @return the types, conditions and flows
   */
  TypeSystem types();

  /**
   * Gives the OpenFGA store of the declaration.
   *
   * @return the adapter that reads and writes its tuples
   */
  Adapter adapter();

  /**
   * Gives the Vert.x that runs blocking work, such as hashing passwords.
   *
   * @return the Vert.x
   */
  Vertx vertx();

  /**
   * Gives the clock that every time is read from.
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
   * Gives a declared type by its name.
   *
   * @param name the type's name, such as {@code acme.dev/Project}
   * @return the type, or a failure with {@code INVALID_ARGUMENT} when it is not declared
   */
  DeclaredType type(String name);

  /**
   * Gives the keys, the type {@code pyrite.dev/Key}.
   *
   * @return the keys, or a failure with {@code FAILED_PRECONDITION} when the declaration has none
   */
  KeyTypeImpl keys();

  /**
   * Gives the accounts, the type {@code pyrite.dev/Account}.
   *
   * @return the accounts, or a failure with {@code FAILED_PRECONDITION} when the declaration has none
   */
  AccountTypeImpl accounts();

  /**
   * Gives the flows of the declaration.
   *
   * @return the flows, or a failure with {@code FAILED_PRECONDITION} when the declaration has no accounts
   */
  FlowType flows();

  /**
   * Makes a resource known: writes its parent and grants its creator the type's creator relation.
   *
   * @param creator the subject that creates it, which needs the type's create permission on the parent
   * @param name the resource's name, such as {@code projects/ledger}
   * @param parent the name of the resource it is under; empty for a type without parents
   * @param context the values that the conditions of grants read
   * @return the resource, or a failure with {@code ALREADY_EXISTS} when it is known already
   */
  Future<Resource> create(Subject creator, String name, String parent, Struct context);

  /**
   * Forgets a resource: its parent, every grant on it, and every grant to it.
   *
   * @param name the resource's name
   * @return a future that completes once it is forgotten, or a failure with {@code FAILED_PRECONDITION} while
   * resources are under it
   */
  Future<Void> forget(String name);

  /**
   * Gives a resource that Pyrite knows.
   *
   * @param name the resource's name
   * @return the resource, or empty when Pyrite does not know it
   */
  Future<Optional<Resource>> get(String name);

  /**
   * Gives a resource that Pyrite knows.
   *
   * @param name the resource's name
   * @return the resource, or a failure with {@code NOT_FOUND} when Pyrite does not know it
   */
  Future<Resource> require(String name);

  /**
   * Reads a tuple as a grant, by the names of the resources.
   *
   * @param tuple the tuple
   * @return the grant, or empty when the tuple's object is not of a declared type
   */
  Optional<Grant> grant(TupleKey tuple);
}
