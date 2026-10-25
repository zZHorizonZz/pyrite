package dev.horizon.pyrite.impl.model;

import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelOptions;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.CelType;
import dev.cel.common.types.SimpleType;
import dev.cel.extensions.CelExtensions;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.horizon.pyrite.schema.v1.IdentifierKind;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public record DeclaredKind(IdentifierKind kind, CelRuntime.Program match, Optional<CelRuntime.Program> normalize) {

  private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]{0,62}");
  private static final int MAX_COMPREHENSION_ITERATIONS = 1_000;

  public static DeclaredKind of(IdentifierKind kind) {
    if (!NAME.matcher(kind.getName()).matches()) {
      throw new IllegalArgumentException("The identifier kind " + kind.getName() + " is not named like "
          + NAME.pattern() + ".");
    }
    if (kind.getMatch().isBlank()) {
      throw new IllegalArgumentException("The identifier kind " + kind.getName() + " has no match.");
    }
    return new DeclaredKind(kind, program(kind, "match", kind.getMatch(), SimpleType.BOOL),
        kind.getNormalize().isBlank() ? Optional.empty()
            : Optional.of(program(kind, "normalize", kind.getNormalize(), SimpleType.STRING)));
  }

  public String name() {
    return kind.getName();
  }

  public boolean receivesCodes() {
    return kind.getReceivesCodes();
  }

  public boolean matches(String value) {
    return Boolean.TRUE.equals(evaluate(match, value));
  }

  public Optional<String> normalized(String value) {
    if (!matches(value)) {
      return Optional.empty();
    }
    String normalized = normalize.map(program -> (String) evaluate(program, value)).orElse(value);
    return matches(normalized) ? Optional.of(normalized) : Optional.empty();
  }

  private Object evaluate(CelRuntime.Program program, String value) {
    try {
      return program.eval(Map.of("value", value));
    } catch (CelEvaluationException failed) {
      return null;
    }
  }

  private static CelRuntime.Program program(IdentifierKind kind, String field, String expression, CelType result) {
    Cel cel = CelFactory.standardCelBuilder()
        .setOptions(CelOptions.current().comprehensionMaxIterations(MAX_COMPREHENSION_ITERATIONS).build())
        .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
        .addCompilerLibraries(CelExtensions.strings())
        .addRuntimeLibraries(CelExtensions.strings())
        .addVar("value", SimpleType.STRING)
        .setResultType(result)
        .build();
    try {
      return cel.createProgram(cel.compile(expression).getAst());
    } catch (CelValidationException | CelEvaluationException invalid) {
      throw new IllegalArgumentException("The " + field + " of the identifier kind " + kind.getName() + " is not a CEL"
          + " expression over the string value that gives a " + result.name().toLowerCase() + ": "
          + invalid.getMessage());
    }
  }
}
