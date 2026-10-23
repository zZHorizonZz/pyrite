CREATE TABLE `acme_organization` (`uid` VARCHAR(36) NOT NULL, `name` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL, `parent` VARCHAR(255) NULL, `display_name` VARCHAR(255) NULL, `labels` JSON NOT NULL, `revision` BIGINT NOT NULL, `create_time` DATETIME(6) NOT NULL, `update_time` DATETIME(6) NOT NULL, `delete_time` DATETIME(6) NULL, `purge_time` DATETIME(6) NULL, CONSTRAINT `pk_acme_organization` PRIMARY KEY (`uid`));

CREATE UNIQUE INDEX `ux_acme_organization_name` ON `acme_organization` (`name`);

ALTER TABLE `acme_organization` COMMENT = 'An organization: the resource acme.dev/Organization, at the top.';

CREATE TABLE `acme_group` (`uid` VARCHAR(36) NOT NULL, `name` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL, `parent` VARCHAR(255) NULL, `display_name` VARCHAR(255) NULL, `labels` JSON NOT NULL, `revision` BIGINT NOT NULL, `create_time` DATETIME(6) NOT NULL, `update_time` DATETIME(6) NOT NULL, `delete_time` DATETIME(6) NULL, `purge_time` DATETIME(6) NULL, CONSTRAINT `pk_acme_group` PRIMARY KEY (`uid`));

CREATE UNIQUE INDEX `ux_acme_group_name` ON `acme_group` (`name`);

CREATE INDEX `ix_acme_group_parent` ON `acme_group` (`parent`);

ALTER TABLE `acme_group` COMMENT = 'A group of an organization: the resource acme.dev/Group.';

CREATE TABLE `acme_project` (`uid` VARCHAR(36) NOT NULL, `name` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL, `parent` VARCHAR(255) NULL, `display_name` VARCHAR(255) NULL, `labels` JSON NOT NULL, `revision` BIGINT NOT NULL, `create_time` DATETIME(6) NOT NULL, `update_time` DATETIME(6) NOT NULL, `delete_time` DATETIME(6) NULL, `purge_time` DATETIME(6) NULL, `billing_account` VARCHAR(64) NULL COMMENT 'The billing account that pays for the project.', CONSTRAINT `pk_acme_project` PRIMARY KEY (`uid`));

CREATE UNIQUE INDEX `ux_acme_project_name` ON `acme_project` (`name`);

CREATE INDEX `ix_acme_project_parent` ON `acme_project` (`parent`);

ALTER TABLE `acme_project` COMMENT = 'A project of an organization: the resource acme.dev/Project.';

CREATE TABLE `acme_role` (`uid` VARCHAR(36) NOT NULL, `name` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL, `parent` VARCHAR(255) NULL, `display_name` VARCHAR(255) NULL, `labels` JSON NOT NULL, `revision` BIGINT NOT NULL, `create_time` DATETIME(6) NOT NULL, `update_time` DATETIME(6) NOT NULL, `delete_time` DATETIME(6) NULL, `purge_time` DATETIME(6) NULL, CONSTRAINT `pk_acme_role` PRIMARY KEY (`uid`));

CREATE UNIQUE INDEX `ux_acme_role_name` ON `acme_role` (`name`);

CREATE INDEX `ix_acme_role_parent` ON `acme_role` (`parent`);

ALTER TABLE `acme_role` COMMENT = 'A role that an organization defines at runtime: the resource acme.dev/Role.';

CREATE TABLE `acme_binding` (`uid` VARCHAR(36) NOT NULL, `name` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL, `parent` VARCHAR(255) NULL, `display_name` VARCHAR(255) NULL, `labels` JSON NOT NULL, `revision` BIGINT NOT NULL, `create_time` DATETIME(6) NOT NULL, `update_time` DATETIME(6) NOT NULL, `delete_time` DATETIME(6) NULL, `purge_time` DATETIME(6) NULL, CONSTRAINT `pk_acme_binding` PRIMARY KEY (`uid`));

CREATE UNIQUE INDEX `ux_acme_binding_name` ON `acme_binding` (`name`);

CREATE INDEX `ix_acme_binding_parent` ON `acme_binding` (`parent`);

ALTER TABLE `acme_binding` COMMENT = 'A role bound to members: the resource acme.dev/Binding.';
