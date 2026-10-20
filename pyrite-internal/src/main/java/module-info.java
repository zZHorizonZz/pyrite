module dev.horizon.pyrite.internal {
  requires transitive dev.horizon.pyrite.api;
  requires com.nimbusds.jose.jwt;
  requires com.github.benmanes.caffeine;

  exports dev.horizon.pyrite.internal to dev.horizon.pyrite.memory, dev.horizon.pyrite.database;

  provides dev.horizon.pyrite.spi.KeyringFactory with dev.horizon.pyrite.internal.ProviderKeyringFactory;
}
