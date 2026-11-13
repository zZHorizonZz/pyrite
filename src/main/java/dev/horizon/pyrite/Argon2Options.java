package dev.horizon.pyrite;

/**
 * The cost of the Argon2id hashes of passwords. The defaults are the second recommended option of RFC 9106: 64 MiB of
 * memory, 3 passes and 4 lanes.
 *
 * <pre>{@code
 * Argon2Options argon2 = new Argon2Options().setMemory(19_456).setIterations(2).setParallelism(1);
 * }</pre>
 *
 * <p>
 * A higher cost makes each guess of a stolen hash slower, and each sign-in with a password too. Argon2 needs at least
 * 8 KiB of memory per lane, so to lower the memory below that of the lanes before, set the lanes first.
 */
public class Argon2Options {

  /** The memory of a hash: 65536 KiB, which is 64 MiB. */
  public static final int DEFAULT_MEMORY = 65_536;
  /** The passes over the memory: 3. */
  public static final int DEFAULT_ITERATIONS = 3;
  /** The lanes: 4. */
  public static final int DEFAULT_PARALLELISM = 4;

  private static final int MIN_MEMORY_PER_LANE = 8;

  private int memory;
  private int iterations;
  private int parallelism;

  /**
   * Creates the default options: 64 MiB of memory, 3 passes and 4 lanes.
   */
  public Argon2Options() {
    memory = DEFAULT_MEMORY;
    iterations = DEFAULT_ITERATIONS;
    parallelism = DEFAULT_PARALLELISM;
  }

  /**
   * Creates a copy of options.
   *
   * @param other the options to copy
   */
  public Argon2Options(Argon2Options other) {
    memory = other.memory;
    iterations = other.iterations;
    parallelism = other.parallelism;
  }

  public int getMemory() {
    return memory;
  }

  /**
   * Sets the memory of a hash.
   *
   * @param memory the memory in KiB, at least 8 per lane; 65536 by default
   * @return these options
   * @throws IllegalArgumentException if the memory is below 8 KiB per lane of these options
   */
  public Argon2Options setMemory(int memory) {
    if (memory < (long) MIN_MEMORY_PER_LANE * parallelism) {
      throw new IllegalArgumentException("The memory is at least " + MIN_MEMORY_PER_LANE + " KiB per lane.");
    }
    this.memory = memory;
    return this;
  }

  public int getIterations() {
    return iterations;
  }

  /**
   * Sets the passes over the memory.
   *
   * @param iterations the number of passes, at least 1; 3 by default
   * @return these options
   * @throws IllegalArgumentException if the number is below 1
   */
  public Argon2Options setIterations(int iterations) {
    this.iterations = atLeastOne(iterations, "iterations");
    return this;
  }

  public int getParallelism() {
    return parallelism;
  }

  /**
   * Sets the lanes of the memory of a hash.
   *
   * @param parallelism the number of lanes, at least 1, with at least 8 KiB of memory each; 4 by default
   * @return these options
   * @throws IllegalArgumentException if the number is below 1, or the memory of these options is below 8 KiB per lane
   */
  public Argon2Options setParallelism(int parallelism) {
    atLeastOne(parallelism, "parallelism");
    if (memory < (long) MIN_MEMORY_PER_LANE * parallelism) {
      throw new IllegalArgumentException("The memory is at least " + MIN_MEMORY_PER_LANE + " KiB per lane.");
    }
    this.parallelism = parallelism;
    return this;
  }

  private static int atLeastOne(int value, String name) {
    if (value < 1) {
      throw new IllegalArgumentException("The " + name + " is at least 1.");
    }
    return value;
  }
}
