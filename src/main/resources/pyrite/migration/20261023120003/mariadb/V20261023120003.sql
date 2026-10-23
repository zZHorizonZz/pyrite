ALTER TABLE `pyrite_account` MODIFY COLUMN `parent` VARCHAR(255) NULL;

ALTER TABLE `pyrite_account` MODIFY COLUMN `display_name` VARCHAR(255) NULL;

ALTER TABLE `pyrite_identifier` MODIFY COLUMN `parent` VARCHAR(255) NULL;

ALTER TABLE `pyrite_identifier` MODIFY COLUMN `display_name` VARCHAR(255) NULL;

ALTER TABLE `pyrite_factor` MODIFY COLUMN `parent` VARCHAR(255) NULL;

ALTER TABLE `pyrite_factor` MODIFY COLUMN `display_name` VARCHAR(255) NULL;

ALTER TABLE `pyrite_session` MODIFY COLUMN `parent` VARCHAR(255) NULL;

ALTER TABLE `pyrite_session` MODIFY COLUMN `display_name` VARCHAR(255) NULL;

ALTER TABLE `pyrite_flow` MODIFY COLUMN `account` VARCHAR(255) NULL COMMENT 'The account; unset for a flow that names no account that exists.';

ALTER TABLE `pyrite_flow` MODIFY COLUMN `identifier` VARCHAR(255) NULL COMMENT 'The identifier that the flow started with, if any.';

ALTER TABLE `pyrite_flow` MODIFY COLUMN `code_mac` LONGBLOB NULL COMMENT 'The MAC of the code that was sent, and when it stops working.';

ALTER TABLE `pyrite_flow` MODIFY COLUMN `challenge` LONGBLOB NULL COMMENT 'The challenge of a passkey ceremony.';

ALTER TABLE `pyrite_flow` MODIFY COLUMN `factor` VARCHAR(255) NULL COMMENT 'The factor that the flow enrolls, if any.';

ALTER TABLE `pyrite_flow` MODIFY COLUMN `request_id` VARCHAR(64) NULL COMMENT 'The id of the last answer''s request, and the flow that it returned.';

ALTER TABLE `pyrite_flow` MODIFY COLUMN `response` LONGBLOB NULL;

ALTER TABLE `pyrite_key` MODIFY COLUMN `parent` VARCHAR(255) NULL;

ALTER TABLE `pyrite_key` MODIFY COLUMN `display_name` VARCHAR(255) NULL;

ALTER TABLE `pyrite_key_version` MODIFY COLUMN `parent` VARCHAR(255) NULL;

ALTER TABLE `pyrite_key_version` MODIFY COLUMN `display_name` VARCHAR(255) NULL;

ALTER TABLE `pyrite_key_version` MODIFY COLUMN `public_key` JSON NULL COMMENT 'The public key of a signing version, as a JWK; empty for the others.';

ALTER TABLE `pyrite_key_version` MODIFY COLUMN `public_key_info` LONGBLOB NULL COMMENT 'The X.509 SubjectPublicKeyInfo of a signing version; empty for the others.';
