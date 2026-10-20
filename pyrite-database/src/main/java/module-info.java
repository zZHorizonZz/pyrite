module dev.horizon.pyrite.database {
  requires transitive dev.horizon.pyrite.api;
  requires transitive dev.horizon.cinder;
  requires transitive io.vertx.sql.client;
  requires transitive com.google.crypto.tink;
  requires dev.horizon.pyrite.internal;
  requires transitive dev.horizon.cinder.protobuf;
  requires dev.horizon.leadseal.protobuf;
  requires com.google.protobuf;

  exports dev.horizon.pyrite.database;

  opens dev.horizon.pyrite.database.schema.v1 to com.google.protobuf, dev.horizon.cinder.protobuf;
}
