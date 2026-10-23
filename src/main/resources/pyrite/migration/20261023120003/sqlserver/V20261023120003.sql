ALTER TABLE [pyrite_account] ALTER COLUMN [parent] NVARCHAR(255) NULL;

ALTER TABLE [pyrite_account] ALTER COLUMN [display_name] NVARCHAR(255) NULL;

DROP INDEX [ix_pyrite_identifier_parent] ON [pyrite_identifier];

ALTER TABLE [pyrite_identifier] ALTER COLUMN [parent] NVARCHAR(255) NULL;

CREATE INDEX [ix_pyrite_identifier_parent] ON [pyrite_identifier] ([parent]);

ALTER TABLE [pyrite_identifier] ALTER COLUMN [display_name] NVARCHAR(255) NULL;

DROP INDEX [ix_pyrite_factor_parent] ON [pyrite_factor];

ALTER TABLE [pyrite_factor] ALTER COLUMN [parent] NVARCHAR(255) NULL;

CREATE INDEX [ix_pyrite_factor_parent] ON [pyrite_factor] ([parent]);

ALTER TABLE [pyrite_factor] ALTER COLUMN [display_name] NVARCHAR(255) NULL;

DROP INDEX [ix_pyrite_session_parent] ON [pyrite_session];

ALTER TABLE [pyrite_session] ALTER COLUMN [parent] NVARCHAR(255) NULL;

CREATE INDEX [ix_pyrite_session_parent] ON [pyrite_session] ([parent]);

ALTER TABLE [pyrite_session] ALTER COLUMN [display_name] NVARCHAR(255) NULL;

ALTER TABLE [pyrite_flow] ALTER COLUMN [account] NVARCHAR(255) NULL;

ALTER TABLE [pyrite_flow] ALTER COLUMN [identifier] NVARCHAR(255) NULL;

ALTER TABLE [pyrite_flow] ALTER COLUMN [code_mac] VARBINARY(MAX) NULL;

ALTER TABLE [pyrite_flow] ALTER COLUMN [challenge] VARBINARY(MAX) NULL;

ALTER TABLE [pyrite_flow] ALTER COLUMN [factor] NVARCHAR(255) NULL;

ALTER TABLE [pyrite_flow] ALTER COLUMN [request_id] NVARCHAR(64) NULL;

ALTER TABLE [pyrite_flow] ALTER COLUMN [response] VARBINARY(MAX) NULL;

DROP INDEX [ix_pyrite_key_parent] ON [pyrite_key];

ALTER TABLE [pyrite_key] ALTER COLUMN [parent] NVARCHAR(255) NULL;

CREATE INDEX [ix_pyrite_key_parent] ON [pyrite_key] ([parent]);

ALTER TABLE [pyrite_key] ALTER COLUMN [display_name] NVARCHAR(255) NULL;

DROP INDEX [ix_pyrite_key_version_parent] ON [pyrite_key_version];

ALTER TABLE [pyrite_key_version] ALTER COLUMN [parent] NVARCHAR(255) NULL;

CREATE INDEX [ix_pyrite_key_version_parent] ON [pyrite_key_version] ([parent]);

ALTER TABLE [pyrite_key_version] ALTER COLUMN [display_name] NVARCHAR(255) NULL;

ALTER TABLE [pyrite_key_version] ALTER COLUMN [public_key] NVARCHAR(MAX) NULL;

ALTER TABLE [pyrite_key_version] ALTER COLUMN [public_key_info] VARBINARY(MAX) NULL;

DECLARE @pyrite_schema_31 sysname = SCHEMA_NAME() EXEC sp_dropextendedproperty @name = N'MS_Description', @level0type = N'SCHEMA', @level0name = @pyrite_schema_31, @level1type = N'TABLE', @level1name = N'pyrite_flow', @level2type = N'COLUMN', @level2name = N'account';

DECLARE @pyrite_schema_32 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The account; unset for a flow that names no account that exists.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_32, @level1type = N'TABLE', @level1name = N'pyrite_flow', @level2type = N'COLUMN', @level2name = N'account';
