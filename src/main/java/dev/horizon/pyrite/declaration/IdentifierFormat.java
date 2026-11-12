package dev.horizon.pyrite.declaration;

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

/**
 * An identifier kind of a declaration, with its CEL expressions compiled: it tells whether a value is of the kind, and
 * gives the value that Pyrite stores and compares.
 *
 * <pre>{@code
 * IdentifierFormat email = IdentifierFormat.of(kind);
 * Optional<String> stored = email.normalize(" Anne@Acme.dev "); // anne@acme.dev
 * }</pre>
 *
 * <p>
 * The expressions take the string {@code value}, with CEL's standard macros and its strings extension, and a
 * comprehension runs at most 1000 iterations. A value whose {@code match} fails to evaluate is of no kind.
 *
 * @param kind the kind, as the declaration declares it
 * @param match the program of {@code match}, which gives a {@code bool}
 * @param normalize the program of {@code normalize}, which gives a {@code string}; empty to keep the value as given
 */
public record IdentifierFormat(IdentifierKind kind, CelRuntime.Program match, Optional<CelRuntime.Program> normalize) {

  private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]{0,62}");
  private static final int MAX_COMPREHENSION_ITERATIONS = 1_000;

  /**
   * Compiles an identifier kind.
   *
   * @param kind the kind, as the declaration declares it
   * @return the format of the kind
   * @throws IllegalArgumentException if the name does not match {@code [a-z][a-z0-9-]{0,62}}, the kind has no match,
   * or an expression does not compile to its type
   */
  public static IdentifierFormat of(IdentifierKind kind) {
    if (!NAME.matcher(kind.getName()).matches()) {
      throw new IllegalArgumentException("The identifier kind " + kind.getName() + " is not named like "
          + NAME.pattern() + ".");
    }
    if (kind.getMatch().isBlank()) {
      throw new IllegalArgumentException("The identifier kind " + kind.getName() + " has no match.");
    }
    return new IdentifierFormat(kind, program(kind, "match", kind.getMatch(), SimpleType.BOOL),
        kind.getNormalize().isBlank() ? Optional.empty()
            : Optional.of(program(kind, "normalize", kind.getNormalize(), SimpleType.STRING)));
  }

  public String name() {
    return kind.getName();
  }

  public boolean receivesCodes() {
    return kind.getReceivesCodes();
  }

  /**
   * Tells whether a value meets the {@code match} of the kind.
   *
   * @param value the value as given, such as {@code Anne@Acme.dev}
   * @return whether the expression gives {@code true}; {@code false} when it fails to evaluate
   */
  public boolean matches(String value) {
    return Boolean.TRUE.equals(evaluate(match, value));
  }

  /**
   * Gives the value that Pyrite stores for a value of the kind.
   *
   * @param value the value as given, such as {@code Anne@Acme.dev}
   * @return the value as {@code normalize} gives it, such as {@code anne@acme.dev}, or empty when the value, or what
   * {@code normalize} gives, does not meet {@code match}
   */
  public Optional<String> normalize(String value) {
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
