package dev.horizon.pyrite;

import java.util.Set;

public record Capabilities(Set<Capability> capabilities, Set<Algorithm> algorithms) {

  public Capabilities {
    capabilities = Set.copyOf(capabilities);
    algorithms = Set.copyOf(algorithms);
  }

  public boolean has(Capability capability) {
    return capabilities.contains(capability);
  }

  public boolean signs(Algorithm algorithm) {
    return algorithms.contains(algorithm);
  }
}
