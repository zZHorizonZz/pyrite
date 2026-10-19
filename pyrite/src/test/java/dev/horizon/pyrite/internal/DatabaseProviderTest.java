package dev.horizon.pyrite.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import dev.horizon.cinder.sqlite.SqliteDriver;
import dev.horizon.pyrite.Encryption;
import dev.horizon.pyrite.EncryptionKey;
import dev.horizon.pyrite.Rotation;
import dev.horizon.pyrite.database.DatabaseProvider;
import dev.horizon.pyrite.spi.Provider;
import io.vertx.core.Vertx;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class DatabaseProviderTest extends KeyringContract {

  private KeysetHandle root;
  private Pool pool;

  @Override
  protected Provider provider(Vertx vertx, Clock clock) throws Exception {
    AeadConfig.register();
    Path file = Files.createTempFile("pyrite", ".db");
    file.toFile().deleteOnExit();
    String url = "jdbc:sqlite:" + file;
    Flyway.configure()
        .dataSource(url, null, null)
        .locations("classpath:pyrite/migration/**/sqlite")
        .table("pyrite_schema_history")
        .load()
        .migrate();
    root = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM);
    pool = JDBCPool.pool(vertx, new JDBCConnectOptions().setJdbcUrl(url), new PoolOptions().setMaxSize(1));
    return DatabaseProvider.open(vertx, pool, new SqliteDriver(), aead(root)).await(10, TimeUnit.SECONDS);
  }

  @Test
  void keysOutliveTheProviderThatMadeThem() throws Exception {
    EncryptionKey seeds = keyring.create("totp-seeds", new Encryption(), Rotation.never()).await(10, TimeUnit.SECONDS);
    byte[] sealed = seeds.encrypt(bytes("seed"), "accounts/42").await(10, TimeUnit.SECONDS);

    ProviderKeyring reopened = new ProviderKeyring(DatabaseProvider.open(vertx, pool, new SqliteDriver(), aead(root))
        .await(10, TimeUnit.SECONDS), clock);

    assertThat(reopened.encryption("totp-seeds").await(10, TimeUnit.SECONDS).decrypt(sealed, "accounts/42").await(10,
        TimeUnit.SECONDS))
        .isEqualTo(bytes("seed"));
  }

  @Test
  void rewrapLetsTheOldRootKeyGo() throws Exception {
    EncryptionKey seeds = keyring.create("totp-seeds", new Encryption(), Rotation.never()).await(10, TimeUnit.SECONDS);
    byte[] sealed = seeds.encrypt(bytes("seed"), "accounts/42").await(10, TimeUnit.SECONDS);

    KeysetHandle.Builder rotated = KeysetHandle.newBuilder(root);
    rotated.addEntry(KeysetHandle.generateEntryFromParameters(PredefinedAeadParameters.AES256_GCM)
        .withRandomId()
        .makePrimary());
    KeysetHandle both = rotated.build();
    KeysetHandle.Builder trimmed = KeysetHandle.newBuilder(both);
    trimmed.removeAt(0);
    KeysetHandle newest = trimmed.build();

    DatabaseProvider rotating = DatabaseProvider.open(vertx, pool, new SqliteDriver(), aead(both)).await(10, TimeUnit.SECONDS);
    assertThatThrownBy(() -> DatabaseProvider.open(vertx, pool, new SqliteDriver(), aead(newest)).await(10, TimeUnit.SECONDS))
        .isInstanceOf(IllegalStateException.class);

    rotating.rewrap().await(10, TimeUnit.SECONDS);

    ProviderKeyring renewed = new ProviderKeyring(DatabaseProvider.open(vertx, pool, new SqliteDriver(), aead(newest))
        .await(10, TimeUnit.SECONDS), clock);
    assertThat(
        renewed.encryption("totp-seeds").await(10, TimeUnit.SECONDS).decrypt(sealed, "accounts/42").await(10, TimeUnit.SECONDS))
        .isEqualTo(bytes("seed"));
  }

  private Aead aead(KeysetHandle keyset) throws GeneralSecurityException {
    return keyset.getPrimitive(RegistryConfiguration.get(), Aead.class);
  }

  private byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }
}
