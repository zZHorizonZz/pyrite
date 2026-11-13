package dev.horizon.pyrite.conformance;

import dev.horizon.pyrite.PyriteFixture;
import dev.horizon.pyrite.conformance.agreement.AgreementPlan;
import dev.horizon.pyrite.conformance.check.CheckPlan;
import dev.horizon.pyrite.conformance.code.CodePlan;
import dev.horizon.pyrite.conformance.declaration.DeclarationPlan;
import dev.horizon.pyrite.conformance.encryption.EncryptionPlan;
import dev.horizon.pyrite.conformance.exchange.ExchangePlan;
import dev.horizon.pyrite.conformance.factor.FactorPlan;
import dev.horizon.pyrite.conformance.grant.GrantPlan;
import dev.horizon.pyrite.conformance.handler.HandlerPlan;
import dev.horizon.pyrite.conformance.identifier.IdentifierPlan;
import dev.horizon.pyrite.conformance.list.ListPlan;
import dev.horizon.pyrite.conformance.object.ObjectPlan;
import dev.horizon.pyrite.conformance.passkey.PasskeyPlan;
import dev.horizon.pyrite.conformance.password.PasswordPlan;
import dev.horizon.pyrite.conformance.provider.ProviderPlan;
import dev.horizon.pyrite.conformance.rotation.RotationPlan;
import dev.horizon.pyrite.conformance.session.SessionPlan;
import dev.horizon.pyrite.conformance.signing.SigningPlan;
import dev.horizon.pyrite.database.RunningDatabase;
import dev.horizon.pyrite.database.TestDatabase;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;

/**
 * Runs every module of every plan in every variant, each on a Pyrite of its own. The variants are grouped by their
 * database, which runs only while its group does, so that one database at a time takes memory.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConformanceTest {

  static final List<Plan> PLANS = List.of(new SessionPlan(), new ExchangePlan(), new CodePlan(),
      new PasswordPlan(), new IdentifierPlan(), new FactorPlan(), new PasskeyPlan(), new HandlerPlan(), new ObjectPlan(),
      new GrantPlan(), new CheckPlan(), new ListPlan(), new AgreementPlan(), new DeclarationPlan(),
      new EncryptionPlan(), new RotationPlan(), new SigningPlan(), new ProviderPlan());

  private TestDatabase current;
  private RunningDatabase running;

  @AfterAll
  void closeDatabase() throws Exception {
    close();
  }

  @TestFactory
  Stream<DynamicNode> databases() {
    return TestDatabase.selected().stream().map(database -> DynamicContainer.dynamicContainer(database.name(),
        PLANS.stream().map(plan -> DynamicContainer.dynamicContainer(plan.name(), Variant.on(database).stream()
            .map(variant -> DynamicContainer.dynamicContainer(variant.toString(), plan.modules().stream()
                .map(module -> module.get()).filter(module -> module.appliesTo(variant) && selected(module))
                .map(module -> DynamicTest.dynamicTest(module.summary(), () -> run(module, variant)))))))));
  }

  /**
   * Tells whether a module runs: every module, or those that the property {@code pyrite.test.module} names by the
   * simple names of their classes, separated by commas.
   */
  private static boolean selected(ConformanceModule module) {
    String names = System.getProperty("pyrite.test.module", "");
    return names.isBlank() || List.of(names.split(",")).contains(module.getClass().getSimpleName());
  }

  private void run(ConformanceModule module, Variant variant) throws Exception {
    try (PyriteFixture pyrite = new PyriteFixture(database(variant.database()), variant.transport())) {
      module.run(new Environment(pyrite, variant));
    }
  }

  /**
   * Gives the running database of a variant: the one that runs, or, after stopping it, the next one.
   */
  private RunningDatabase database(TestDatabase wanted) throws Exception {
    if (wanted != current) {
      close();
      running = wanted.open();
      current = wanted;
    }
    return running;
  }

  private void close() throws Exception {
    if (running != null) {
      running.close();
      current.stop();
      running = null;
      current = null;
    }
  }
}
