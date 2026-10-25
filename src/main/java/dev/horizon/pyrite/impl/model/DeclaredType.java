package dev.horizon.pyrite.impl.model;

import dev.horizon.pyrite.schema.v1.Permissions;
import dev.horizon.pyrite.schema.v1.Type;
import java.util.List;
import java.util.Map;

public record DeclaredType(Type type, Map<String, Definition> relations) {

  public DeclaredType {
    relations = Map.copyOf(relations);
  }

  public String name() {
    return type.getName();
  }

  public String singular() {
    return type.getDefinition().getType();
  }

  public String plural() {
    return type.getPlural();
  }

  public List<String> parents() {
    return type.getParentsList();
  }

  public Permissions permissions() {
    return type.getPermissions();
  }

  public List<String> grantable() {
    return type.getGrantableList();
  }

  public String creator() {
    return type.getCreator();
  }

  public String resource(String id) {
    return plural() + "/" + id;
  }

  public String object(String id) {
    return singular() + ":" + id;
  }

  public String id(String resource) {
    return resource.substring(plural().length() + 1);
  }
}
