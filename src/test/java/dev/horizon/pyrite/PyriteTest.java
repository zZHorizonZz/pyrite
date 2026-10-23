package dev.horizon.pyrite;

import dev.horizon.pyrite.database.RunningDatabase;
import dev.horizon.pyrite.database.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class PyriteTest {

  protected Pyrite pyrite;

  private RunningDatabase database;

  @BeforeAll
  void openDatabase() {
    database = TestDatabase.chosen().open();
  }

  @AfterAll
  void closeDatabase() throws Exception {
    if (database != null) {
      database.close();
    }
  }

  @BeforeEach
  void startPyrite() throws Exception {
    pyrite = new Pyrite(database);
  }

  @AfterEach
  void stopPyrite() throws Exception {
    if (pyrite != null) {
      pyrite.close();
    }
  }
}
