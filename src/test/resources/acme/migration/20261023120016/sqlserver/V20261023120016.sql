CREATE TABLE [acme_organization] ([uid] NVARCHAR(36) NOT NULL, [name] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [parent] NVARCHAR(255) NULL, [display_name] NVARCHAR(255) NULL, [labels] NVARCHAR(MAX) NOT NULL, [revision] BIGINT NOT NULL, [create_time] DATETIME2(6) NOT NULL, [update_time] DATETIME2(6) NOT NULL, [delete_time] DATETIME2(6) NULL, [purge_time] DATETIME2(6) NULL, CONSTRAINT [pk_acme_organization] PRIMARY KEY ([uid]));

CREATE UNIQUE INDEX [ux_acme_organization_name] ON [acme_organization] ([name]);

DECLARE @acme_schema_2 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'An organization: the resource acme.dev/Organization, at the top.', @level0type = N'SCHEMA', @level0name = @acme_schema_2, @level1type = N'TABLE', @level1name = N'acme_organization';

CREATE TABLE [acme_group] ([uid] NVARCHAR(36) NOT NULL, [name] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [parent] NVARCHAR(255) NULL, [display_name] NVARCHAR(255) NULL, [labels] NVARCHAR(MAX) NOT NULL, [revision] BIGINT NOT NULL, [create_time] DATETIME2(6) NOT NULL, [update_time] DATETIME2(6) NOT NULL, [delete_time] DATETIME2(6) NULL, [purge_time] DATETIME2(6) NULL, CONSTRAINT [pk_acme_group] PRIMARY KEY ([uid]));

CREATE UNIQUE INDEX [ux_acme_group_name] ON [acme_group] ([name]);

CREATE INDEX [ix_acme_group_parent] ON [acme_group] ([parent]);

DECLARE @acme_schema_6 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A group of an organization: the resource acme.dev/Group.', @level0type = N'SCHEMA', @level0name = @acme_schema_6, @level1type = N'TABLE', @level1name = N'acme_group';

CREATE TABLE [acme_project] ([uid] NVARCHAR(36) NOT NULL, [name] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [parent] NVARCHAR(255) NULL, [display_name] NVARCHAR(255) NULL, [labels] NVARCHAR(MAX) NOT NULL, [revision] BIGINT NOT NULL, [create_time] DATETIME2(6) NOT NULL, [update_time] DATETIME2(6) NOT NULL, [delete_time] DATETIME2(6) NULL, [purge_time] DATETIME2(6) NULL, [billing_account] NVARCHAR(64) NULL, CONSTRAINT [pk_acme_project] PRIMARY KEY ([uid]));

CREATE UNIQUE INDEX [ux_acme_project_name] ON [acme_project] ([name]);

CREATE INDEX [ix_acme_project_parent] ON [acme_project] ([parent]);

DECLARE @acme_schema_10 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A project of an organization: the resource acme.dev/Project.', @level0type = N'SCHEMA', @level0name = @acme_schema_10, @level1type = N'TABLE', @level1name = N'acme_project';

DECLARE @acme_schema_11 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The billing account that pays for the project.', @level0type = N'SCHEMA', @level0name = @acme_schema_11, @level1type = N'TABLE', @level1name = N'acme_project', @level2type = N'COLUMN', @level2name = N'billing_account';

CREATE TABLE [acme_role] ([uid] NVARCHAR(36) NOT NULL, [name] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [parent] NVARCHAR(255) NULL, [display_name] NVARCHAR(255) NULL, [labels] NVARCHAR(MAX) NOT NULL, [revision] BIGINT NOT NULL, [create_time] DATETIME2(6) NOT NULL, [update_time] DATETIME2(6) NOT NULL, [delete_time] DATETIME2(6) NULL, [purge_time] DATETIME2(6) NULL, CONSTRAINT [pk_acme_role] PRIMARY KEY ([uid]));

CREATE UNIQUE INDEX [ux_acme_role_name] ON [acme_role] ([name]);

CREATE INDEX [ix_acme_role_parent] ON [acme_role] ([parent]);

DECLARE @acme_schema_15 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A role that an organization defines at runtime: the resource acme.dev/Role.', @level0type = N'SCHEMA', @level0name = @acme_schema_15, @level1type = N'TABLE', @level1name = N'acme_role';

CREATE TABLE [acme_binding] ([uid] NVARCHAR(36) NOT NULL, [name] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [parent] NVARCHAR(255) NULL, [display_name] NVARCHAR(255) NULL, [labels] NVARCHAR(MAX) NOT NULL, [revision] BIGINT NOT NULL, [create_time] DATETIME2(6) NOT NULL, [update_time] DATETIME2(6) NOT NULL, [delete_time] DATETIME2(6) NULL, [purge_time] DATETIME2(6) NULL, CONSTRAINT [pk_acme_binding] PRIMARY KEY ([uid]));

CREATE UNIQUE INDEX [ux_acme_binding_name] ON [acme_binding] ([name]);

CREATE INDEX [ix_acme_binding_parent] ON [acme_binding] ([parent]);

DECLARE @acme_schema_19 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A role bound to members: the resource acme.dev/Binding.', @level0type = N'SCHEMA', @level0name = @acme_schema_19, @level1type = N'TABLE', @level1name = N'acme_binding';
