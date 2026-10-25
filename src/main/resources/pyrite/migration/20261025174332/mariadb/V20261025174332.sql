CREATE TABLE `pyrite_key` (`uid` VARCHAR(36) NOT NULL, `name` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL, `parent` VARCHAR(255) NULL, `display_name` VARCHAR(255) NULL, `labels` JSON NOT NULL, `revision` BIGINT NOT NULL, `create_time` DATETIME(6) NOT NULL, `update_time` DATETIME(6) NOT NULL, `delete_time` DATETIME(6) NULL, `purge_time` DATETIME(6) NULL, `purpose` VARCHAR(16) NOT NULL COMMENT 'ENCRYPTION, MAC or SIGNING.', `algorithm` VARCHAR(16) NOT NULL COMMENT 'AES256_GCM, HMAC_SHA256, RS256, ES256 or EDDSA.', `rotation_period` BIGINT NOT NULL COMMENT 'How often the key gets a new primary version, in seconds; 0 for never.', `next_rotation_time` DATETIME(6) NULL COMMENT 'When the key next gets a new primary version, if it rotates.', `primary_version` VARCHAR(255) NOT NULL COMMENT 'The name of the version that encrypts and signs.', CONSTRAINT `pk_pyrite_key` PRIMARY KEY (`uid`));

CREATE UNIQUE INDEX `ux_pyrite_key_name` ON `pyrite_key` (`name`);

CREATE INDEX `ix_pyrite_key_parent` ON `pyrite_key` (`parent`);

ALTER TABLE `pyrite_key` COMMENT = 'A key: the resource pyrite.dev/Key, with the standard fields and its own.';

CREATE TABLE `pyrite_key_version` (`uid` VARCHAR(36) NOT NULL, `name` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL, `parent` VARCHAR(255) NULL, `display_name` VARCHAR(255) NULL, `labels` JSON NOT NULL, `revision` BIGINT NOT NULL, `create_time` DATETIME(6) NOT NULL, `update_time` DATETIME(6) NOT NULL, `delete_time` DATETIME(6) NULL, `purge_time` DATETIME(6) NULL, `number` BIGINT NOT NULL COMMENT '1 for the first version of the key, and one more for each after it.', `state` VARCHAR(16) NOT NULL COMMENT 'ENABLED or DISABLED.', `algorithm` VARCHAR(16) NOT NULL, `public_key` JSON NULL COMMENT 'The public key of a signing version, as a JWK; empty for the others.', `sealed` LONGBLOB NOT NULL COMMENT 'The key material, encrypted by the root key.', `public_key_info` LONGBLOB NULL COMMENT 'The X.509 SubjectPublicKeyInfo of a signing version; empty for the others.', CONSTRAINT `pk_pyrite_key_version` PRIMARY KEY (`uid`));

CREATE UNIQUE INDEX `ux_pyrite_key_version_name` ON `pyrite_key_version` (`name`);

CREATE INDEX `ix_pyrite_key_version_parent` ON `pyrite_key_version` (`parent`);

ALTER TABLE `pyrite_key_version` COMMENT = 'A version of a key: the resource pyrite.dev/KeyVersion, under its key.';

CREATE TABLE `pyrite_account` (`uid` VARCHAR(36) NOT NULL, `name` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL, `parent` VARCHAR(255) NULL, `display_name` VARCHAR(255) NULL, `labels` JSON NOT NULL, `revision` BIGINT NOT NULL, `create_time` DATETIME(6) NOT NULL, `update_time` DATETIME(6) NOT NULL, `delete_time` DATETIME(6) NULL, `purge_time` DATETIME(6) NULL, `kind` VARCHAR(16) NOT NULL COMMENT 'HUMAN or SERVICE.', CONSTRAINT `pk_pyrite_account` PRIMARY KEY (`uid`));

CREATE UNIQUE INDEX `ux_pyrite_account_name` ON `pyrite_account` (`name`);

ALTER TABLE `pyrite_account` COMMENT = 'An account: the resource pyrite.dev/Account, whose principals are user:<id>.';

CREATE TABLE `pyrite_identifier` (`uid` VARCHAR(36) NOT NULL, `name` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL, `parent` VARCHAR(255) NULL, `display_name` VARCHAR(255) NULL, `labels` JSON NOT NULL, `revision` BIGINT NOT NULL, `create_time` DATETIME(6) NOT NULL, `update_time` DATETIME(6) NOT NULL, `delete_time` DATETIME(6) NULL, `purge_time` DATETIME(6) NULL, `kind` VARCHAR(16) NOT NULL COMMENT 'EMAIL, PHONE or USERNAME.', `value` VARCHAR(320) COLLATE utf8mb4_bin NOT NULL COMMENT 'The address, the number in E.164, or the username, as Pyrite normalized it.', `verified` BOOLEAN NOT NULL, `primary` BOOLEAN NOT NULL COMMENT 'Whether the account prefers it among its identifiers; the caller sets it.', CONSTRAINT `pk_pyrite_identifier` PRIMARY KEY (`uid`));

CREATE UNIQUE INDEX `ux_pyrite_identifier_name` ON `pyrite_identifier` (`name`);

CREATE INDEX `ix_pyrite_identifier_parent` ON `pyrite_identifier` (`parent`);

CREATE UNIQUE INDEX `ux_pyrite_identifier_kind_value` ON `pyrite_identifier` (`kind`, `value`);

ALTER TABLE `pyrite_identifier` COMMENT = 'An identifier of an account: the resource pyrite.dev/Identifier, under its account.';

CREATE TABLE `pyrite_factor` (`uid` VARCHAR(36) NOT NULL, `name` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL, `parent` VARCHAR(255) NULL, `display_name` VARCHAR(255) NULL, `labels` JSON NOT NULL, `revision` BIGINT NOT NULL, `create_time` DATETIME(6) NOT NULL, `update_time` DATETIME(6) NOT NULL, `delete_time` DATETIME(6) NULL, `purge_time` DATETIME(6) NULL, `kind` VARCHAR(16) NOT NULL COMMENT 'PASSWORD, TOTP, PASSKEY or RECOVERY_CODES.', `state` VARCHAR(16) NOT NULL COMMENT 'PENDING while it is enrolled, then ACTIVE.', `secret` LONGBLOB NOT NULL COMMENT 'The password''s Argon2id hash, the TOTP secret encrypted, the passkey''s credential, or the recovery codes'' MACs.', `credential_id` VARCHAR(255) NOT NULL COMMENT 'A passkey''s credential id; empty for the others.', `counter` BIGINT NOT NULL COMMENT 'A passkey''s signature counter, or the last TOTP step that signed in.', `user_verified` BOOLEAN NOT NULL COMMENT 'Whether a passkey verified its user when it was registered.', CONSTRAINT `pk_pyrite_factor` PRIMARY KEY (`uid`));

CREATE UNIQUE INDEX `ux_pyrite_factor_name` ON `pyrite_factor` (`name`);

CREATE INDEX `ix_pyrite_factor_parent` ON `pyrite_factor` (`parent`);

CREATE INDEX `ix_pyrite_factor_credential_id` ON `pyrite_factor` (`credential_id`);

ALTER TABLE `pyrite_factor` COMMENT = 'A factor of an account: the resource pyrite.dev/Factor, under its account.';

CREATE TABLE `pyrite_session` (`uid` VARCHAR(36) NOT NULL, `name` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL, `parent` VARCHAR(255) NULL, `display_name` VARCHAR(255) NULL, `labels` JSON NOT NULL, `revision` BIGINT NOT NULL, `create_time` DATETIME(6) NOT NULL, `update_time` DATETIME(6) NOT NULL, `delete_time` DATETIME(6) NULL, `purge_time` DATETIME(6) NULL, `token_mac` LONGBLOB NOT NULL COMMENT 'The MAC of the token''s secret.', `expire_time` DATETIME(6) NOT NULL, `idle_expire_time` DATETIME(6) NOT NULL, CONSTRAINT `pk_pyrite_session` PRIMARY KEY (`uid`));

CREATE UNIQUE INDEX `ux_pyrite_session_name` ON `pyrite_session` (`name`);

CREATE INDEX `ix_pyrite_session_parent` ON `pyrite_session` (`parent`);

ALTER TABLE `pyrite_session` COMMENT = 'A session of an account: the resource pyrite.dev/Session, under its account.';

CREATE TABLE `pyrite_flow` (`id` VARCHAR(36) NOT NULL, `definition` VARCHAR(63) NOT NULL, `account` VARCHAR(255) NULL COMMENT 'The account; unset for a flow that names no account that exists.', `identifier` VARCHAR(255) NULL COMMENT 'The identifier that the flow started with, if any.', `revision` BIGINT NOT NULL, `stage` INT NOT NULL COMMENT 'The stage of the definition that the flow is in.', `proofs` JSON NOT NULL COMMENT 'What the answers of the stage have proven, as JSON.', `answers` INT NOT NULL, `expire_time` DATETIME(6) NOT NULL, `code_mac` LONGBLOB NULL COMMENT 'The MAC of the code that was sent, and when it stops working.', `code_expire_time` DATETIME(6) NULL, `challenge` LONGBLOB NULL COMMENT 'The challenge of a passkey ceremony.', `factor` VARCHAR(255) NULL COMMENT 'The factor that the flow enrolls, if any.', `request_id` VARCHAR(64) NULL COMMENT 'The id of the last answer''s request, and the flow that it returned.', `response` LONGBLOB NULL, `done` BOOLEAN NOT NULL COMMENT 'Whether the flow is done; a done flow takes no answers.', CONSTRAINT `pk_pyrite_flow` PRIMARY KEY (`id`));

ALTER TABLE `pyrite_flow` COMMENT = 'A flow in progress. It is not a resource: only the Authentication service reads and writes it.';

CREATE TABLE `pyrite_declaration` (`id` VARCHAR(36) NOT NULL, `declaration` LONGBLOB NOT NULL COMMENT 'The pyrite.schema.v1.Declaration, serialized.', `store_id` VARCHAR(26) NOT NULL COMMENT 'The OpenFGA store of the grants.', `model_id` VARCHAR(26) NOT NULL COMMENT 'The authorization model that the declaration wrote to the store.', `create_time` DATETIME(6) NOT NULL, CONSTRAINT `pk_pyrite_declaration` PRIMARY KEY (`id`));

CREATE INDEX `ix_pyrite_declaration_create_time` ON `pyrite_declaration` (`create_time`);

ALTER TABLE `pyrite_declaration` COMMENT = 'A declaration that pyrite.schema.v1.Schema wrote, with the OpenFGA store and model that it wrote for it. The latest
is the one that every service reads.';

CREATE TABLE `pyrite_node` (`name` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL COMMENT 'The resource''s name, such as projects/ledger.', `type` VARCHAR(255) NOT NULL COMMENT 'The declared type, such as acme.dev/Project.', `parent` VARCHAR(255) NULL COMMENT 'The name of the resource it is under; unset for a resource at the top.', `create_time` DATETIME(6) NOT NULL, CONSTRAINT `pk_pyrite_node` PRIMARY KEY (`name`));

CREATE INDEX `ix_pyrite_node_parent` ON `pyrite_node` (`parent`);

ALTER TABLE `pyrite_node` COMMENT = 'A resource that Pyrite knows: its type and the resource it is under. The application keeps the resource itself;
Pyrite keeps where it sits, so that grants reach it from its parents and nothing is made twice.';
