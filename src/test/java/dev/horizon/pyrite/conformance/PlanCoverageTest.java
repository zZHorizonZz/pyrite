package dev.horizon.pyrite.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks the conformance suite against its own classes: every module runs in a plan, names its behavior, and every
 * requirement is held by some module.
 */
class PlanCoverageTest {

  private static final String PACKAGE = ConformanceModule.class.getPackageName();
  private static final String REQUIREMENT = Requirement.class.getName().replace('.', '/');

  private static Path root;
  private static List<Class<?>> modules;

  @BeforeAll
  static void scan() throws IOException, URISyntaxException {
    root = Path.of(ConformanceModule.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    Path directory = root.resolve(PACKAGE.replace('.', '/'));
    try (Stream<Path> files = Files.walk(directory)) {
      modules = files.filter(file -> file.toString().endsWith(".class")).map(PlanCoverageTest::load)
          .filter(type -> ConformanceModule.class.isAssignableFrom(type) && type != ConformanceModule.class)
          .toList();
    }
    assertThat(modules).as("the modules on the class path").isNotEmpty();
  }

  @Test
  @DisplayName("every module runs in a plan")
  void registration() {
    Set<Class<?>> registered = ConformanceTest.PLANS.stream().flatMap(plan -> plan.modules().stream())
        .map(module -> module.get().getClass()).collect(Collectors.toSet());

    assertThat(concrete().filter(module -> !registered.contains(module)).map(Class::getName))
        .as("the modules that no plan runs").isEmpty();
  }

  @Test
  @DisplayName("every module names its behavior with @Summary")
  void summary() {
    assertThat(concrete().filter(module -> !module.isAnnotationPresent(Summary.class)).map(Class::getName))
        .as("the modules without @Summary").isEmpty();
  }

  @Test
  @DisplayName("every requirement is held by some module")
  void requirements() throws IOException {
    Set<String> used = new TreeSet<>();
    for (Class<?> module : modules) {
      Path file = root.resolve(module.getName().replace('.', '/') + ".class");
      for (PoolEntry entry : ClassFile.of().parse(file).constantPool()) {
        if (entry instanceof FieldRefEntry field && field.owner().asInternalName().equals(REQUIREMENT)) {
          used.add(field.name().stringValue());
        }
      }
    }

    assertThat(Arrays.stream(Requirement.values()).map(Requirement::name).filter(name -> !used.contains(name)))
        .as("the requirements that no module holds").isEmpty();
  }

  private static Stream<Class<?>> concrete() {
    return modules.stream().filter(module -> !Modifier.isAbstract(module.getModifiers()));
  }

  private static Class<?> load(Path file) {
    String name = root.relativize(file).toString().replace('\\', '/').replace('/', '.');
    try {
      return Class.forName(name.substring(0, name.length() - ".class".length()), false,
          PlanCoverageTest.class.getClassLoader());
    } catch (ClassNotFoundException impossible) {
      throw new IllegalStateException("A class file has no class: " + file, impossible);
    }
  }
}
