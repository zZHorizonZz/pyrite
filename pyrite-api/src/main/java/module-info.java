module dev.horizon.pyrite.api {
  requires transitive dev.horizon.pyrite.common;
  requires transitive dev.horizon.aquery.api;
  requires transitive io.vertx.core;

  exports dev.horizon.pyrite;
  exports dev.horizon.pyrite.spi;

  uses dev.horizon.pyrite.spi.KeyringFactory;
}
