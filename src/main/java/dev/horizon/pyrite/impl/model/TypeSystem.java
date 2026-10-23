package dev.horizon.pyrite.impl.model;

import dev.horizon.pyrite.schema.v1.FlowDefinition;
import dev.horizon.spectrum.openfga.v1.Condition;
import dev.horizon.spectrum.openfga.v1.Userset;
import dev.horizon.spectrum.openfga.v1.WriteAuthorizationModelRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A declaration, compiled: its types, their relations and hierarchy, its conditions and its flows, and the
 * authorization model that it gives OpenFGA.
 *
 * <p>
 * Compiling checks every rule of the declaration, and fails with {@link IllegalArgumentException} at the first rule
 * that it breaks. It also splits each relation that reads from the parent: OpenFGA answers the parts that do not, as
 * hidden relations named {@code <relation>.<n>}, and Pyrite walks the rest.
 */
public interface TypeSystem {

  String USER = "user";
  String PARENT = "parent";
  String KEY = "pyrite.dev/Key";
  String KEY_VERSION = "pyrite.dev/KeyVersion";
  String ACCOUNT = "pyrite.dev/Account";
  String IDENTIFIER = "pyrite.dev/Identifier";
  String FACTOR = "pyrite.dev/Factor";
  String SESSION = "pyrite.dev/Session";

  /**
   * Gives a declared flow.
   *
   * @param name the flow's name, such as {@code sign-in}
   * @return the flow, or empty when it is not declared
   */
  Optional<FlowDefinition> flow(String name);

  /**
   * Gives every declared flow.
   *
   * @return the flows
   */
  List<FlowDefinition> flows();

  /**
   * Gives a declared type by its name.
   *
   * @param name the type's name, such as {@code acme.dev/Project}
   * @return the type, or empty when it is not declared
   */
  Optional<DeclaredType> type(String name);

  /**
   * Gives a declared type by its singular name, which is its OpenFGA type.
   *
   * @param singular the singular name, such as {@code project}
   * @return the type, or empty when it is not declared
   */
  Optional<DeclaredType> singular(String singular);

  /**
   * Gives the declared type of a resource's name, by the name's first segment.
   *
   * @param resource the resource's name, such as {@code projects/ledger}
   * @return the type, or empty when no type has the plural
   */
  Optional<DeclaredType> typeOf(String resource);

  /**
   * Gives every declared type, with the types that Pyrite adds beneath keys and accounts.
   *
   * @return the types
   */
  List<DeclaredType> types();

  /**
   * Gives the declared conditions.
   *
   * @return the conditions, by name
   */
  Map<String, Condition> conditions();

  /**
   * Gives a type's parent types.
   *
   * @param type the type
   * @return the parent types
   */
  List<DeclaredType> parents(DeclaredType type);

  /**
   * Gives the types whose resources can be under a type's.
   *
   * @param type the type
   * @return the child types
   */
  List<DeclaredType> children(DeclaredType type);

  /**
   * Gives every type above a type in the hierarchy.
   *
   * @param type the type
   * @return the ancestor types
   */
  Set<DeclaredType> ancestors(DeclaredType type);

  /**
   * Tells whether a relation reads from the parent, directly or through another relation of the type.
   *
   * @param type the type
   * @param relation the relation
   * @return whether Pyrite walks the hierarchy for it
   */
  boolean readsFromParent(DeclaredType type, String relation);

  /**
   * Gives the rewrite that Pyrite follows for a relation.
   *
   * @param type the type
   * @param relation the relation
   * @return for a relation that reads from the parent, its steps to the parent and the hidden relations that OpenFGA
   * answers; for any other, its declared rewrite
   */
  Userset rewrite(DeclaredType type, String relation);

  /**
   * Gives the relations that a relation reaches, on any type, whose tuples OpenFGA holds. A query lists the objects of
   * each of them.
   *
   * @param type the type
   * @param relation the relation
   * @return the leaves
   */
  Set<Leaf> leaves(DeclaredType type, String relation);

  /**
   * Gives the authorization model of the declaration, with the hidden relations and without the {@code parent} steps.
   *
   * @param store the OpenFGA store's id
   * @return the request that writes the model
   */
  WriteAuthorizationModelRequest model(String store);
}
