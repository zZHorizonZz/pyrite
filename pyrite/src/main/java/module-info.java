module dev.horizon.pyrite {
  requires transitive dev.horizon.pyrite.api;
  requires transitive dev.horizon.cinder;
  requires transitive io.vertx.sql.client;
  requires transitive com.google.crypto.tink;
  requires dev.horizon.cinder.protobuf;
  requires dev.horizon.leadseal.protobuf;
  requires com.google.protobuf;
  requires com.github.benmanes.caffeine;
  requires com.nimbusds.jose.jwt;
  requires org.bouncycastle.pkix;
  requires org.bouncycastle.provider;

  exports dev.horizon.pyrite.memory;
  exports dev.horizon.pyrite.database;

  opens dev.horizon.pyrite.database.schema.v1 to com.google.protobuf, dev.horizon.cinder.protobuf;

  provides dev.horizon.pyrite.spi.KeyringFactory with dev.horizon.pyrite.internal.ProviderKeyringFactory;
}
