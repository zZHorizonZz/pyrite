package dev.horizon.pyrite.impl.model;

import dev.horizon.pyrite.schema.v1.FlowDefinition;
import dev.horizon.spectrum.openfga.v1.Condition;
import dev.horizon.spectrum.openfga.v1.WriteAuthorizationModelRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A declaration, compiled: its types, their relations and hierarchy, its conditions and its flows, and the
 * authorization model that it gives OpenFGA.
 *
 * <p>
 * Compiling checks every rule of the declaration, and fails with {@link IllegalArgumentException} at the first rule
 * that it breaks. A type with parents gets the relation {@code parent} in the model, whose tuples Pyrite writes when a
 * resource is made known, so OpenFGA reads a relation from the parent itself.
 */
public interface TypeSystem {

  String USER = "user";
  String PARENT = "parent";
  String KEY = "pyrite.dev/Key";
  String ACCOUNT = "pyrite.dev/Account";
  String OWNER = "owner";

  /** The relations that Pyrite checks on a key. */
  List<String> KEY_RELATIONS = List.of("viewer", "admin", "encrypter", "decrypter", "signer", "verifier");

  /** The relations that Pyrite checks on an account. */
  List<String> ACCOUNT_RELATIONS = List.of(OWNER, "viewer", "admin");

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
   * @return the flows, in the order of the declaration
   */
  List<FlowDefinition> flows();

  /**
   * Gives the declared identifier kinds.
   *
   * @return the kinds, in the order of the declaration
   */
  List<DeclaredKind> identifierKinds();

  /**
   * Gives a declared identifier kind by its name.
   *
   * @param name the kind's name, such as {@code email}
   * @return the kind, or empty when it is not declared
   */
  Optional<DeclaredKind> identifierKind(String name);

  /**
   * Gives the kind of a value as a person typed it: the first declared kind whose match the value meets.
   *
   * @param value the value, such as {@code anne@example.com}
   * @return the kind, or empty when no kind matches
   */
  Optional<DeclaredKind> identifierKindOf(String value);

  /**
   * Gives a declared type by its name.
   *
   * @param name the type's name, such as {@code acme.dev/Project}
   * @return the type, or empty when it is not declared
   */
  Optional<DeclaredType> type(String name);

  /**
   * Gives a declared type by its singular name, the OpenFGA type.
   *
   * @param singular the singular name, such as {@code project}
   * @return the type, or empty when no type has the name
   */
  Optional<DeclaredType> singular(String singular);

  /**
   * Gives the type of a resource by the first segment of its name.
   *
   * @param resource the resource's name, such as {@code projects/ledger}
   * @return the type, or empty when no type has the plural name
   */
  Optional<DeclaredType> typeOf(String resource);

  /**
   * Gives every declared type.
   *
   * @return the types, in the order of the declaration
   */
  List<DeclaredType> types();

  /**
   * Gives the declared conditions.
   *
   * @return the conditions, by name
   */
  Map<String, Condition> conditions();

  /**
   * Gives the parent types of a type.
   *
   * @param type the type
   * @return its parent types, in the order of its declaration
   */
  List<DeclaredType> parents(DeclaredType type);

  /**
   * Gives the authorization model of the declaration.
   *
   * @param store the OpenFGA store to write it to
   * @return the request that writes it
   */
  WriteAuthorizationModelRequest model(String store);
}
