CREATE TABLE `pyrite_vault` (`name` VARCHAR(16) NOT NULL, `sealed` LONGBLOB NOT NULL, `create_time` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), CONSTRAINT `pk_pyrite_vault` PRIMARY KEY (`name`));

ALTER TABLE `pyrite_vault` COMMENT = 'The vault key, encrypted under the root key. It encrypts the material of every version, so rotating the root key
encrypts this one row again.';

CREATE TABLE `pyrite_key` (`name` VARCHAR(63) COLLATE utf8mb4_bin NOT NULL, `purpose` VARCHAR(16) NOT NULL COMMENT 'ENCRYPTION, MAC or SIGNING.', `algorithm` VARCHAR(16) NULL COMMENT 'The algorithm of a signing key: RS256, ES256 or EDDSA.', `rotation_millis` BIGINT NOT NULL COMMENT 'How often the key gets a new primary version, in milliseconds; 0 for never.', `pending_millis` BIGINT NOT NULL COMMENT 'How long a new version is pending before it becomes primary, in milliseconds.', `lifetime_millis` BIGINT NULL COMMENT 'How long a version works after it is made, in milliseconds; none for a version that works until it is disabled.', `revision` BIGINT NOT NULL, `create_time` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), CONSTRAINT `pk_pyrite_key` PRIMARY KEY (`name`));

ALTER TABLE `pyrite_key` COMMENT = 'A key. Every change of the key or of one of its versions moves its revision.';

CREATE TABLE `pyrite_version` (`key_name` VARCHAR(63) COLLATE utf8mb4_bin NOT NULL, `number` BIGINT NOT NULL COMMENT 'The number of the version in its key: 1 for the first, and one more for each after it.', `origin` VARCHAR(16) NOT NULL COMMENT 'GENERATED, EXPORTED or UPLOADED.', `state` VARCHAR(16) NOT NULL COMMENT 'PENDING, PRIMARY, LIVE, DISABLED, EXPIRED or DESTROYED.', `state_time` DATETIME(6) NULL COMMENT 'When a pending version becomes primary, since when a primary is primary, or when a version was disabled, expired or
was destroyed. None for a live version.', `create_time` DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)), `sealed` LONGBLOB NULL COMMENT 'The secret material, encrypted under the vault key: the AES or HMAC key, or the PKCS#8 private key. None for a
public key alone and for a destroyed version.', `public_key` LONGBLOB NULL COMMENT 'The X.509 SubjectPublicKeyInfo of a signing version.', `expire_time` DATETIME(6) NULL COMMENT 'When the version expires, as the key''s validity said when it was made. None for a version that never does.', CONSTRAINT `pk_pyrite_version` PRIMARY KEY (`key_name`, `number`));

ALTER TABLE `pyrite_version` COMMENT = 'A version of a key.';

ALTER TABLE `pyrite_version` ADD CONSTRAINT `fk_pyrite_version_key_name` FOREIGN KEY (`key_name`) REFERENCES `pyrite_key` (`name`) ON DELETE CASCADE;
