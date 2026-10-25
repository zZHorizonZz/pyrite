/**
 * Pyrite: accounts, keys and who may do what, on Vert.x and a SQL database. The JAR contains cel-java, RE2/J and ANTLR
 * under {@code dev.horizon.pyrite.shaded}, which evaluate the identifier kinds of a declaration, so that they need no
 * modules of their own.
 */
module dev.horizon.pyrite {
  requires transitive io.vertx.core;
  requires transitive io.vertx.grpc.server;
  requires transitive io.vertx.grpc.client;
  requires transitive io.vertx.grpc.common;
  requires transitive io.vertx.sql.client;
  requires transitive com.google.protobuf;
  requires transitive com.google.crypto.tink;
  requires transitive dev.horizon.cinder;
  requires transitive dev.horizon.spectrum;
  requires dev.horizon.aquery.api;
  requires dev.horizon.basepoint;
  requires dev.horizon.cinder.protobuf;
  requires dev.horizon.leadseal.protobuf;
  requires com.nimbusds.jose.jwt;
  requires org.bouncycastle.provider;
  requires com.webauthn4j.core;
  requires com.google.common;

  exports dev.horizon.pyrite;
  exports dev.horizon.pyrite.schema;
  exports dev.horizon.pyrite.access;
  exports dev.horizon.pyrite.crypto;
  exports dev.horizon.pyrite.authentication;
  exports dev.horizon.pyrite.schema.v1;
  exports dev.horizon.pyrite.access.v1;
  exports dev.horizon.pyrite.crypto.v1;
  exports dev.horizon.pyrite.authentication.v1;

  opens dev.horizon.pyrite.schema.keys.v1;
  opens dev.horizon.pyrite.schema.authentication.v1;
  opens dev.horizon.pyrite.schema.declaration.v1;
}
