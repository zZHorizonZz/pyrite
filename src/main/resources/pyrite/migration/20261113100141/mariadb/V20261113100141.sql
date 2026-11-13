CREATE TABLE `pyrite_account` (`uid` UUID NOT NULL DEFAULT (UUID()), `name` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL, `parent` VARCHAR(255) NULL, `display_name` VARCHAR(255) NULL, `labels` JSON NOT NULL DEFAULT '{}', `revision` BIGINT NOT NULL DEFAULT 1, `create_time` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), `update_time` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), `delete_time` DATETIME(6) NULL, `purge_time` DATETIME(6) NULL, `kind` ENUM('KIND_UNSPECIFIED', 'HUMAN', 'SERVICE') NOT NULL, CONSTRAINT `pk_pyrite_account` PRIMARY KEY (`uid`));

CREATE UNIQUE INDEX `ux_pyrite_account_name` ON `pyrite_account` (`name`);

ALTER TABLE `pyrite_account` COMMENT = 'An account: the resource pyrite.dev/Account, whose principals are user:<id>.';

CREATE TABLE `pyrite_identifier` (`uid` UUID NOT NULL DEFAULT (UUID()), `name` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL, `parent` VARCHAR(255) COLLATE utf8mb4_bin NULL, `display_name` VARCHAR(255) NULL, `labels` JSON NOT NULL DEFAULT '{}', `revision` BIGINT NOT NULL DEFAULT 1, `create_time` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), `update_time` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), `delete_time` DATETIME(6) NULL, `purge_time` DATETIME(6) NULL, `kind` VARCHAR(16) NOT NULL COMMENT 'EMAIL, PHONE or USERNAME.', `value` VARCHAR(320) COLLATE utf8mb4_bin NOT NULL COMMENT 'The address, the number in E.164, or the username, as Pyrite normalized it.', `verified` BOOLEAN NOT NULL, `primary` BOOLEAN NOT NULL COMMENT 'Whether the account prefers it among its identifiers; the caller sets it.', CONSTRAINT `pk_pyrite_identifier` PRIMARY KEY (`uid`));

CREATE UNIQUE INDEX `ux_pyrite_identifier_name` ON `pyrite_identifier` (`name`);

CREATE INDEX `ix_pyrite_identifier_parent` ON `pyrite_identifier` (`parent`);

CREATE UNIQUE INDEX `ux_pyrite_identifier_kind_value` ON `pyrite_identifier` (`kind`, `value`);

ALTER TABLE `pyrite_identifier` COMMENT = 'An identifier of an account: the resource pyrite.dev/Identifier, under its account.';

CREATE TABLE `pyrite_factor` (`uid` UUID NOT NULL DEFAULT (UUID()), `name` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL, `parent` VARCHAR(255) COLLATE utf8mb4_bin NULL, `display_name` VARCHAR(255) NULL, `labels` JSON NOT NULL DEFAULT '{}', `revision` BIGINT NOT NULL DEFAULT 1, `create_time` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), `update_time` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), `delete_time` DATETIME(6) NULL, `purge_time` DATETIME(6) NULL, `kind` ENUM('KIND_UNSPECIFIED', 'PASSWORD', 'TOTP', 'PASSKEY', 'RECOVERY_CODES', 'PROVIDER') NOT NULL, `state` ENUM('STATE_UNSPECIFIED', 'PENDING', 'ACTIVE') NOT NULL COMMENT 'PENDING while it is enrolled, then ACTIVE.', `secret` LONGBLOB NOT NULL COMMENT 'The password''s Argon2id hash, the TOTP secret encrypted, the passkey''s credential, the recovery codes'' MACs, or the
provider account as its sign-in proved it.', `credential_id` VARCHAR(255) NOT NULL COMMENT 'A passkey''s credential id, or the provider and the id of a provider account, as google:1093...; empty for the
others.', `counter` BIGINT NOT NULL COMMENT 'A passkey''s signature counter, or the last TOTP step that signed in.', `user_verified` BOOLEAN NOT NULL COMMENT 'Whether a passkey verified its user when it was registered.', CONSTRAINT `pk_pyrite_factor` PRIMARY KEY (`uid`));

CREATE UNIQUE INDEX `ux_pyrite_factor_name` ON `pyrite_factor` (`name`);

CREATE INDEX `ix_pyrite_factor_parent` ON `pyrite_factor` (`parent`);

CREATE INDEX `ix_pyrite_factor_credential_id` ON `pyrite_factor` (`credential_id`);

ALTER TABLE `pyrite_factor` COMMENT = 'A factor of an account: the resource pyrite.dev/Factor, under its account.';

CREATE TABLE `pyrite_session` (`uid` UUID NOT NULL DEFAULT (UUID()), `name` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL, `parent` VARCHAR(255) COLLATE utf8mb4_bin NULL, `display_name` VARCHAR(255) NULL, `labels` JSON NOT NULL DEFAULT '{}', `revision` BIGINT NOT NULL DEFAULT 1, `create_time` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), `update_time` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), `delete_time` DATETIME(6) NULL, `purge_time` DATETIME(6) NULL, `expire_time` DATETIME(6) NOT NULL, `idle_expire_time` DATETIME(6) NOT NULL, CONSTRAINT `pk_pyrite_session` PRIMARY KEY (`uid`));

CREATE UNIQUE INDEX `ux_pyrite_session_name` ON `pyrite_session` (`name`);

CREATE INDEX `ix_pyrite_session_parent` ON `pyrite_session` (`parent`);

ALTER TABLE `pyrite_session` COMMENT = 'A session of an account: the resource pyrite.dev/Session, under its account.';

CREATE TABLE `pyrite_provider_link` (`factor` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL COMMENT 'The factor.', `provider` VARCHAR(63) NOT NULL COMMENT 'The name of the provider in the options, such as google.', `account_id` VARCHAR(255) NOT NULL COMMENT 'The id of the account at the provider.', CONSTRAINT `pk_pyrite_provider_link` PRIMARY KEY (`factor`));

CREATE UNIQUE INDEX `ux_pyrite_provider_link_provider_account_id` ON `pyrite_provider_link` (`provider`, `account_id`);

ALTER TABLE `pyrite_provider_link` COMMENT = 'The account at a provider that a factor of the kind PROVIDER links. The provider and the id are unique together, so
that no account at a provider is linked to two accounts. It goes with its factor.';

CREATE TABLE `pyrite_refresh_token` (`id` VARCHAR(36) NOT NULL COMMENT 'The id of the token, before the dot in the token.', `session` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL COMMENT 'The session.', `mac` LONGBLOB NOT NULL COMMENT 'The MAC of the token''s secret, which the MAC key of the options signs.', `create_time` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), `use_time` DATETIME(6) NULL COMMENT 'When a refresh traded the token for the next; unset while it works.', CONSTRAINT `pk_pyrite_refresh_token` PRIMARY KEY (`id`));

CREATE INDEX `ix_pyrite_refresh_token_session` ON `pyrite_refresh_token` (`session`);

ALTER TABLE `pyrite_refresh_token` COMMENT = 'A refresh token of a session. A refresh trades it for the next, so a token works once; one that comes back after its
trade ends its session. It goes with its session.';

CREATE TABLE `pyrite_exchange` (`id` VARCHAR(36) NOT NULL, `definition` VARCHAR(63) NOT NULL, `account` VARCHAR(255) COLLATE utf8mb4_bin NULL COMMENT 'The account; unset for an exchange that names no account that exists.', `identifier` VARCHAR(255) NULL COMMENT 'The identifier that the exchange started with, if any.', `revision` BIGINT NOT NULL, `stage` INT NOT NULL COMMENT 'The stage of the definition that the exchange is in.', `proofs` JSON NOT NULL COMMENT 'What the answers of the stage have proven, as JSON.', `answers` INT NOT NULL, `expire_time` DATETIME(6) NOT NULL, `nonce` LONGBLOB NULL COMMENT 'The random nonce of the stage, which a passkey answer signs, and when it, and what the verifiers keep with it, stop
working.', `nonce_expire_time` DATETIME(6) NULL, `state` JSON NOT NULL COMMENT 'What the verifiers keep for the stage, such as the MAC of the code that was sent, as JSON by the name of the
answer.', `factor` VARCHAR(255) NULL COMMENT 'The factor that the exchange enrolls, if any.', `request_id` VARCHAR(64) NULL COMMENT 'The id of the last answer''s request, and the exchange that it returned.', `response` LONGBLOB NULL, `done` BOOLEAN NOT NULL COMMENT 'Whether the exchange is done; a done exchange takes no answers.', `provider_account` LONGBLOB NULL COMMENT 'The account at a provider that the exchange creates an account for, as a ProviderAccount.', CONSTRAINT `pk_pyrite_exchange` PRIMARY KEY (`id`));

ALTER TABLE `pyrite_exchange` COMMENT = 'An exchange in progress. It is not a resource: only the Authentication service reads and writes it.';

CREATE TABLE `pyrite_declaration` (`id` VARCHAR(36) NOT NULL, `declaration` LONGBLOB NOT NULL COMMENT 'The pyrite.schema.v1.Declaration, serialized.', `store_id` VARCHAR(26) NOT NULL COMMENT 'The OpenFGA store of the grants.', `model_id` VARCHAR(26) NOT NULL COMMENT 'The authorization model that the declaration wrote to the store.', `create_time` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), CONSTRAINT `pk_pyrite_declaration` PRIMARY KEY (`id`));

CREATE INDEX `ix_pyrite_declaration_create_time` ON `pyrite_declaration` (`create_time`);

ALTER TABLE `pyrite_declaration` COMMENT = 'A declaration that pyrite.schema.v1.Schema wrote, with the OpenFGA store and model that it wrote for it. The latest
is the one that every service reads.';

CREATE TABLE `pyrite_node` (`name` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL COMMENT 'The resource''s name, such as projects/ledger.', `type` VARCHAR(255) NOT NULL COMMENT 'The declared type, such as acme.dev/Project.', `parent` VARCHAR(255) COLLATE utf8mb4_bin NULL COMMENT 'The name of the resource it is under; unset for a resource at the top.', `create_time` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), CONSTRAINT `pk_pyrite_node` PRIMARY KEY (`name`));

CREATE INDEX `ix_pyrite_node_parent` ON `pyrite_node` (`parent`);

ALTER TABLE `pyrite_node` COMMENT = 'A resource that Pyrite knows: its type and the resource it is under. The application keeps the resource itself;
Pyrite keeps where it sits, so that grants reach it from its parents and nothing is made twice.';

CREATE TABLE `pyrite_key` (`uid` UUID NOT NULL DEFAULT (UUID()), `name` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL, `parent` VARCHAR(255) NULL, `display_name` VARCHAR(255) NULL, `labels` JSON NOT NULL DEFAULT '{}', `revision` BIGINT NOT NULL DEFAULT 1, `create_time` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), `update_time` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), `delete_time` DATETIME(6) NULL, `purge_time` DATETIME(6) NULL, `purpose` ENUM('PURPOSE_UNSPECIFIED', 'ENCRYPTION', 'MAC', 'SIGNING') NOT NULL, `algorithm` ENUM('ALGORITHM_UNSPECIFIED', 'AES256_GCM', 'HMAC_SHA256', 'RS256', 'ES256', 'EDDSA') NOT NULL, `rotation_period` BIGINT NOT NULL COMMENT 'How often the key gets a new primary version, in seconds; 0 for never.', `next_rotation_time` DATETIME(6) NULL COMMENT 'When the key next gets a new primary version, if it rotates.', `primary_version` VARCHAR(255) NOT NULL COMMENT 'The name of the version that encrypts and signs.', CONSTRAINT `pk_pyrite_key` PRIMARY KEY (`uid`));

CREATE UNIQUE INDEX `ux_pyrite_key_name` ON `pyrite_key` (`name`);

CREATE INDEX `ix_pyrite_key_parent` ON `pyrite_key` (`parent`);

ALTER TABLE `pyrite_key` COMMENT = 'A key: the resource pyrite.dev/Key, with the standard fields and its own.';

CREATE TABLE `pyrite_key_version` (`uid` UUID NOT NULL DEFAULT (UUID()), `name` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL, `parent` VARCHAR(255) COLLATE utf8mb4_bin NULL, `display_name` VARCHAR(255) NULL, `labels` JSON NOT NULL DEFAULT '{}', `revision` BIGINT NOT NULL DEFAULT 1, `create_time` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), `update_time` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), `delete_time` DATETIME(6) NULL, `purge_time` DATETIME(6) NULL, `number` BIGINT NOT NULL COMMENT '1 for the first version of the key, and one more for each after it.', `state` ENUM('STATE_UNSPECIFIED', 'ENABLED', 'DISABLED') NOT NULL, `algorithm` ENUM('ALGORITHM_UNSPECIFIED', 'AES256_GCM', 'HMAC_SHA256', 'RS256', 'ES256', 'EDDSA') NOT NULL, `public_key` JSON NULL COMMENT 'The public key of a signing version, as a JWK; empty for the others.', `sealed` LONGBLOB NOT NULL COMMENT 'The key material, encrypted by the root key.', `public_key_info` LONGBLOB NULL COMMENT 'The X.509 SubjectPublicKeyInfo of a signing version; empty for the others.', CONSTRAINT `pk_pyrite_key_version` PRIMARY KEY (`uid`));

CREATE UNIQUE INDEX `ux_pyrite_key_version_name` ON `pyrite_key_version` (`name`);

CREATE UNIQUE INDEX `ux_pyrite_key_version_parent_number` ON `pyrite_key_version` (`parent`, `number`);

ALTER TABLE `pyrite_key_version` COMMENT = 'A version of a key: the resource pyrite.dev/KeyVersion, under its key.';

ALTER TABLE `pyrite_account` ADD CONSTRAINT `fk_pyrite_account_name` FOREIGN KEY (`name`) REFERENCES `pyrite_node` (`name`) ON DELETE CASCADE;

ALTER TABLE `pyrite_identifier` ADD CONSTRAINT `fk_pyrite_identifier_parent` FOREIGN KEY (`parent`) REFERENCES `pyrite_account` (`name`) ON DELETE CASCADE;

ALTER TABLE `pyrite_factor` ADD CONSTRAINT `fk_pyrite_factor_parent` FOREIGN KEY (`parent`) REFERENCES `pyrite_account` (`name`) ON DELETE CASCADE;

ALTER TABLE `pyrite_session` ADD CONSTRAINT `fk_pyrite_session_parent` FOREIGN KEY (`parent`) REFERENCES `pyrite_account` (`name`) ON DELETE CASCADE;

ALTER TABLE `pyrite_provider_link` ADD CONSTRAINT `fk_pyrite_provider_link_factor` FOREIGN KEY (`factor`) REFERENCES `pyrite_factor` (`name`) ON DELETE CASCADE;

ALTER TABLE `pyrite_refresh_token` ADD CONSTRAINT `fk_pyrite_refresh_token_session` FOREIGN KEY (`session`) REFERENCES `pyrite_session` (`name`) ON DELETE CASCADE;

ALTER TABLE `pyrite_exchange` ADD CONSTRAINT `fk_pyrite_exchange_account` FOREIGN KEY (`account`) REFERENCES `pyrite_account` (`name`) ON DELETE CASCADE;

ALTER TABLE `pyrite_node` ADD CONSTRAINT `fk_pyrite_node_parent` FOREIGN KEY (`parent`) REFERENCES `pyrite_node` (`name`) ON DELETE RESTRICT;

ALTER TABLE `pyrite_key` ADD CONSTRAINT `fk_pyrite_key_name` FOREIGN KEY (`name`) REFERENCES `pyrite_node` (`name`) ON DELETE CASCADE;

ALTER TABLE `pyrite_key_version` ADD CONSTRAINT `fk_pyrite_key_version_parent` FOREIGN KEY (`parent`) REFERENCES `pyrite_key` (`name`) ON DELETE CASCADE;

CREATE TRIGGER `rw_pyrite_account` BEFORE UPDATE ON `pyrite_account` FOR EACH ROW BEGIN
  SET NEW.`revision` = (OLD.`revision` + 1);
  SET NEW.`update_time` = (UTC_TIMESTAMP(6));
END;

CREATE TRIGGER `rw_pyrite_identifier` BEFORE UPDATE ON `pyrite_identifier` FOR EACH ROW BEGIN
  SET NEW.`revision` = (OLD.`revision` + 1);
  SET NEW.`update_time` = (UTC_TIMESTAMP(6));
END;

CREATE TRIGGER `rw_pyrite_factor` BEFORE UPDATE ON `pyrite_factor` FOR EACH ROW BEGIN
  SET NEW.`revision` = (OLD.`revision` + 1);
  SET NEW.`update_time` = (UTC_TIMESTAMP(6));
END;

CREATE TRIGGER `rw_pyrite_session` BEFORE UPDATE ON `pyrite_session` FOR EACH ROW BEGIN
  SET NEW.`revision` = (OLD.`revision` + 1);
  SET NEW.`update_time` = (UTC_TIMESTAMP(6));
END;

CREATE TRIGGER `rw_pyrite_key` BEFORE UPDATE ON `pyrite_key` FOR EACH ROW BEGIN
  SET NEW.`revision` = (OLD.`revision` + 1);
  SET NEW.`update_time` = (UTC_TIMESTAMP(6));
END;

CREATE TRIGGER `rw_pyrite_key_version` BEFORE UPDATE ON `pyrite_key_version` FOR EACH ROW BEGIN
  SET NEW.`revision` = (OLD.`revision` + 1);
  SET NEW.`update_time` = (UTC_TIMESTAMP(6));
END;
