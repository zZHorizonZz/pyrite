CREATE TABLE [pyrite_account] ([uid] UNIQUEIDENTIFIER NOT NULL CONSTRAINT [df_pyrite_account_uid] DEFAULT NEWID(), [name] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [parent] NVARCHAR(255) NULL, [display_name] NVARCHAR(255) NULL, [labels] NVARCHAR(MAX) NOT NULL CONSTRAINT [df_pyrite_account_labels] DEFAULT N'{}', [revision] BIGINT NOT NULL CONSTRAINT [df_pyrite_account_revision] DEFAULT 1, [create_time] DATETIME2(6) NOT NULL CONSTRAINT [df_pyrite_account_create_time] DEFAULT SYSUTCDATETIME(), [update_time] DATETIME2(6) NOT NULL CONSTRAINT [df_pyrite_account_update_time] DEFAULT SYSUTCDATETIME(), [delete_time] DATETIME2(6) NULL, [purge_time] DATETIME2(6) NULL, [kind] NVARCHAR(63) NOT NULL, CONSTRAINT [pk_pyrite_account] PRIMARY KEY ([uid]), CONSTRAINT [ck_pyrite_account_kind] CHECK (([kind] IN (N'KIND_UNSPECIFIED', N'HUMAN', N'SERVICE'))));

CREATE UNIQUE INDEX [ux_pyrite_account_name] ON [pyrite_account] ([name]);

DECLARE @pyrite_schema_2 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'An account: the resource pyrite.dev/Account, whose principals are user:<id>.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_2, @level1type = N'TABLE', @level1name = N'pyrite_account';

CREATE TABLE [pyrite_identifier] ([uid] UNIQUEIDENTIFIER NOT NULL CONSTRAINT [df_pyrite_identifier_uid] DEFAULT NEWID(), [name] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [parent] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NULL, [display_name] NVARCHAR(255) NULL, [labels] NVARCHAR(MAX) NOT NULL CONSTRAINT [df_pyrite_identifier_labels] DEFAULT N'{}', [revision] BIGINT NOT NULL CONSTRAINT [df_pyrite_identifier_revision] DEFAULT 1, [create_time] DATETIME2(6) NOT NULL CONSTRAINT [df_pyrite_identifier_create_time] DEFAULT SYSUTCDATETIME(), [update_time] DATETIME2(6) NOT NULL CONSTRAINT [df_pyrite_identifier_update_time] DEFAULT SYSUTCDATETIME(), [delete_time] DATETIME2(6) NULL, [purge_time] DATETIME2(6) NULL, [kind] NVARCHAR(16) NOT NULL, [value] NVARCHAR(320) COLLATE Latin1_General_100_BIN2 NOT NULL, [verified] BIT NOT NULL, [primary] BIT NOT NULL, [claim] NVARCHAR(340) NOT NULL, CONSTRAINT [pk_pyrite_identifier] PRIMARY KEY ([uid]));

CREATE UNIQUE INDEX [ux_pyrite_identifier_name] ON [pyrite_identifier] ([name]);

CREATE INDEX [ix_pyrite_identifier_parent] ON [pyrite_identifier] ([parent]);

CREATE INDEX [ix_pyrite_identifier_kind_value] ON [pyrite_identifier] ([kind], [value]);

CREATE UNIQUE INDEX [ux_pyrite_identifier_claim] ON [pyrite_identifier] ([claim]);

DECLARE @pyrite_schema_8 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'An identifier of an account: the resource pyrite.dev/Identifier, under its account.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_8, @level1type = N'TABLE', @level1name = N'pyrite_identifier';

DECLARE @pyrite_schema_9 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'EMAIL, PHONE or USERNAME.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_9, @level1type = N'TABLE', @level1name = N'pyrite_identifier', @level2type = N'COLUMN', @level2name = N'kind';

DECLARE @pyrite_schema_10 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The address, the number in E.164, or the username, as Pyrite normalized it.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_10, @level1type = N'TABLE', @level1name = N'pyrite_identifier', @level2type = N'COLUMN', @level2name = N'value';

DECLARE @pyrite_schema_11 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'Whether the account prefers it among its identifiers; the caller sets it.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_11, @level1type = N'TABLE', @level1name = N'pyrite_identifier', @level2type = N'COLUMN', @level2name = N'primary';

DECLARE @pyrite_schema_12 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The kind and the value, as email:anne@acme.dev, once the identifier is verified, so that no two verified
identifiers hold one value; the identifier''s own name while it is not verified, so that any account can add a value
without learning or blocking whether another holds it.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_12, @level1type = N'TABLE', @level1name = N'pyrite_identifier', @level2type = N'COLUMN', @level2name = N'claim';

CREATE TABLE [pyrite_factor] ([uid] UNIQUEIDENTIFIER NOT NULL CONSTRAINT [df_pyrite_factor_uid] DEFAULT NEWID(), [name] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [parent] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NULL, [display_name] NVARCHAR(255) NULL, [labels] NVARCHAR(MAX) NOT NULL CONSTRAINT [df_pyrite_factor_labels] DEFAULT N'{}', [revision] BIGINT NOT NULL CONSTRAINT [df_pyrite_factor_revision] DEFAULT 1, [create_time] DATETIME2(6) NOT NULL CONSTRAINT [df_pyrite_factor_create_time] DEFAULT SYSUTCDATETIME(), [update_time] DATETIME2(6) NOT NULL CONSTRAINT [df_pyrite_factor_update_time] DEFAULT SYSUTCDATETIME(), [delete_time] DATETIME2(6) NULL, [purge_time] DATETIME2(6) NULL, [kind] NVARCHAR(63) NOT NULL, [state] NVARCHAR(63) NOT NULL, [secret] VARBINARY(MAX) NOT NULL, [credential_id] NVARCHAR(255) NOT NULL, [counter] BIGINT NOT NULL, [user_verified] BIT NOT NULL, CONSTRAINT [pk_pyrite_factor] PRIMARY KEY ([uid]), CONSTRAINT [ck_pyrite_factor_kind] CHECK (([kind] IN (N'KIND_UNSPECIFIED', N'PASSWORD', N'TOTP', N'PASSKEY', N'RECOVERY_CODES', N'PROVIDER'))), CONSTRAINT [ck_pyrite_factor_state] CHECK (([state] IN (N'STATE_UNSPECIFIED', N'PENDING', N'ACTIVE'))));

CREATE UNIQUE INDEX [ux_pyrite_factor_name] ON [pyrite_factor] ([name]);

CREATE INDEX [ix_pyrite_factor_parent] ON [pyrite_factor] ([parent]);

CREATE INDEX [ix_pyrite_factor_credential_id] ON [pyrite_factor] ([credential_id]);

DECLARE @pyrite_schema_17 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A factor of an account: the resource pyrite.dev/Factor, under its account.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_17, @level1type = N'TABLE', @level1name = N'pyrite_factor';

DECLARE @pyrite_schema_18 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'PENDING while it is enrolled, then ACTIVE.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_18, @level1type = N'TABLE', @level1name = N'pyrite_factor', @level2type = N'COLUMN', @level2name = N'state';

DECLARE @pyrite_schema_19 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The password''s Argon2id hash, the TOTP secret encrypted, the passkey''s credential, the recovery codes'' MACs, or the
provider account as its sign-in proved it.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_19, @level1type = N'TABLE', @level1name = N'pyrite_factor', @level2type = N'COLUMN', @level2name = N'secret';

DECLARE @pyrite_schema_20 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A passkey''s credential id, or the provider and the id of a provider account, as google:1093...; empty for the
others.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_20, @level1type = N'TABLE', @level1name = N'pyrite_factor', @level2type = N'COLUMN', @level2name = N'credential_id';

DECLARE @pyrite_schema_21 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A passkey''s signature counter, or the last TOTP step that signed in.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_21, @level1type = N'TABLE', @level1name = N'pyrite_factor', @level2type = N'COLUMN', @level2name = N'counter';

DECLARE @pyrite_schema_22 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'Whether a passkey verified its user when it was registered.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_22, @level1type = N'TABLE', @level1name = N'pyrite_factor', @level2type = N'COLUMN', @level2name = N'user_verified';

CREATE TABLE [pyrite_session] ([uid] UNIQUEIDENTIFIER NOT NULL CONSTRAINT [df_pyrite_session_uid] DEFAULT NEWID(), [name] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [parent] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NULL, [display_name] NVARCHAR(255) NULL, [labels] NVARCHAR(MAX) NOT NULL CONSTRAINT [df_pyrite_session_labels] DEFAULT N'{}', [revision] BIGINT NOT NULL CONSTRAINT [df_pyrite_session_revision] DEFAULT 1, [create_time] DATETIME2(6) NOT NULL CONSTRAINT [df_pyrite_session_create_time] DEFAULT SYSUTCDATETIME(), [update_time] DATETIME2(6) NOT NULL CONSTRAINT [df_pyrite_session_update_time] DEFAULT SYSUTCDATETIME(), [delete_time] DATETIME2(6) NULL, [purge_time] DATETIME2(6) NULL, [expire_time] DATETIME2(6) NOT NULL, [idle_expire_time] DATETIME2(6) NOT NULL, CONSTRAINT [pk_pyrite_session] PRIMARY KEY ([uid]));

CREATE UNIQUE INDEX [ux_pyrite_session_name] ON [pyrite_session] ([name]);

CREATE INDEX [ix_pyrite_session_parent] ON [pyrite_session] ([parent]);

DECLARE @pyrite_schema_26 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A session of an account: the resource pyrite.dev/Session, under its account.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_26, @level1type = N'TABLE', @level1name = N'pyrite_session';

CREATE TABLE [pyrite_provider_link] ([factor] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [provider] NVARCHAR(63) NOT NULL, [account_id] NVARCHAR(255) NOT NULL, CONSTRAINT [pk_pyrite_provider_link] PRIMARY KEY ([factor]));

CREATE UNIQUE INDEX [ux_pyrite_provider_link_provider_account_id] ON [pyrite_provider_link] ([provider], [account_id]);

DECLARE @pyrite_schema_29 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The account at a provider that a factor of the kind PROVIDER links. The provider and the id are unique together, so
that no account at a provider is linked to two accounts. It goes with its factor.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_29, @level1type = N'TABLE', @level1name = N'pyrite_provider_link';

DECLARE @pyrite_schema_30 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The factor.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_30, @level1type = N'TABLE', @level1name = N'pyrite_provider_link', @level2type = N'COLUMN', @level2name = N'factor';

DECLARE @pyrite_schema_31 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The name of the provider in the options, such as google.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_31, @level1type = N'TABLE', @level1name = N'pyrite_provider_link', @level2type = N'COLUMN', @level2name = N'provider';

DECLARE @pyrite_schema_32 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The id of the account at the provider.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_32, @level1type = N'TABLE', @level1name = N'pyrite_provider_link', @level2type = N'COLUMN', @level2name = N'account_id';

CREATE TABLE [pyrite_refresh_token] ([id] NVARCHAR(36) NOT NULL, [session] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [mac] VARBINARY(MAX) NOT NULL, [create_time] DATETIME2(6) NOT NULL CONSTRAINT [df_pyrite_refresh_token_create_time] DEFAULT SYSUTCDATETIME(), [use_time] DATETIME2(6) NULL, CONSTRAINT [pk_pyrite_refresh_token] PRIMARY KEY ([id]));

CREATE INDEX [ix_pyrite_refresh_token_session] ON [pyrite_refresh_token] ([session]);

DECLARE @pyrite_schema_35 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A refresh token of a session. A refresh trades it for the next, so a token works once; one that comes back after its
trade ends its session. It goes with its session.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_35, @level1type = N'TABLE', @level1name = N'pyrite_refresh_token';

DECLARE @pyrite_schema_36 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The id of the token, before the dot in the token.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_36, @level1type = N'TABLE', @level1name = N'pyrite_refresh_token', @level2type = N'COLUMN', @level2name = N'id';

DECLARE @pyrite_schema_37 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The session.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_37, @level1type = N'TABLE', @level1name = N'pyrite_refresh_token', @level2type = N'COLUMN', @level2name = N'session';

DECLARE @pyrite_schema_38 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The MAC of the token''s secret, which the MAC key of the options signs.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_38, @level1type = N'TABLE', @level1name = N'pyrite_refresh_token', @level2type = N'COLUMN', @level2name = N'mac';

DECLARE @pyrite_schema_39 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'When a refresh traded the token for the next; unset while it works.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_39, @level1type = N'TABLE', @level1name = N'pyrite_refresh_token', @level2type = N'COLUMN', @level2name = N'use_time';

CREATE TABLE [pyrite_exchange] ([id] NVARCHAR(36) NOT NULL, [definition] NVARCHAR(63) NOT NULL, [account] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NULL, [identifier] NVARCHAR(255) NULL, [revision] BIGINT NOT NULL, [stage] INT NOT NULL, [proofs] NVARCHAR(MAX) NOT NULL, [answers] INT NOT NULL, [expire_time] DATETIME2(6) NOT NULL, [nonce] VARBINARY(MAX) NULL, [nonce_expire_time] DATETIME2(6) NULL, [state] NVARCHAR(MAX) NOT NULL, [factor] NVARCHAR(255) NULL, [request_id] NVARCHAR(64) NULL, [response] VARBINARY(MAX) NULL, [done] BIT NOT NULL, [provider_account] VARBINARY(MAX) NULL, CONSTRAINT [pk_pyrite_exchange] PRIMARY KEY ([id]));

DECLARE @pyrite_schema_41 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'An exchange in progress. It is not a resource: only the Authentication service reads and writes it.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_41, @level1type = N'TABLE', @level1name = N'pyrite_exchange';

DECLARE @pyrite_schema_42 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The account; unset for an exchange that names no account that exists.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_42, @level1type = N'TABLE', @level1name = N'pyrite_exchange', @level2type = N'COLUMN', @level2name = N'account';

DECLARE @pyrite_schema_43 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The identifier that the exchange started with, if any.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_43, @level1type = N'TABLE', @level1name = N'pyrite_exchange', @level2type = N'COLUMN', @level2name = N'identifier';

DECLARE @pyrite_schema_44 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The stage of the definition that the exchange is in.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_44, @level1type = N'TABLE', @level1name = N'pyrite_exchange', @level2type = N'COLUMN', @level2name = N'stage';

DECLARE @pyrite_schema_45 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'What the answers of the stage have proven, as JSON.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_45, @level1type = N'TABLE', @level1name = N'pyrite_exchange', @level2type = N'COLUMN', @level2name = N'proofs';

DECLARE @pyrite_schema_46 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The random nonce of the stage, which a passkey answer signs, and when it, and what the verifiers keep with it, stop
working.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_46, @level1type = N'TABLE', @level1name = N'pyrite_exchange', @level2type = N'COLUMN', @level2name = N'nonce';

DECLARE @pyrite_schema_47 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'What the verifiers keep for the stage, such as the MAC of the code that was sent, as JSON by the name of the
answer.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_47, @level1type = N'TABLE', @level1name = N'pyrite_exchange', @level2type = N'COLUMN', @level2name = N'state';

DECLARE @pyrite_schema_48 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The factor that the exchange enrolls, if any.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_48, @level1type = N'TABLE', @level1name = N'pyrite_exchange', @level2type = N'COLUMN', @level2name = N'factor';

DECLARE @pyrite_schema_49 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The id of the last answer''s request, and the exchange that it returned.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_49, @level1type = N'TABLE', @level1name = N'pyrite_exchange', @level2type = N'COLUMN', @level2name = N'request_id';

DECLARE @pyrite_schema_50 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'Whether the exchange is done; a done exchange takes no answers.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_50, @level1type = N'TABLE', @level1name = N'pyrite_exchange', @level2type = N'COLUMN', @level2name = N'done';

DECLARE @pyrite_schema_51 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The account at a provider that the exchange creates an account for, as a ProviderAccount.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_51, @level1type = N'TABLE', @level1name = N'pyrite_exchange', @level2type = N'COLUMN', @level2name = N'provider_account';

CREATE TABLE [pyrite_declaration] ([id] NVARCHAR(36) NOT NULL, [declaration] VARBINARY(MAX) NOT NULL, [store_id] NVARCHAR(26) NOT NULL, [model_id] NVARCHAR(26) NOT NULL, [create_time] DATETIME2(6) NOT NULL CONSTRAINT [df_pyrite_declaration_create_time] DEFAULT SYSUTCDATETIME(), [previous_id] NVARCHAR(36) NOT NULL, CONSTRAINT [pk_pyrite_declaration] PRIMARY KEY ([id]));

CREATE INDEX [ix_pyrite_declaration_create_time] ON [pyrite_declaration] ([create_time]);

CREATE UNIQUE INDEX [ux_pyrite_declaration_previous_id] ON [pyrite_declaration] ([previous_id]);

DECLARE @pyrite_schema_55 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A declaration that pyrite.schema.v1.Schema wrote, with the store of grants and the model that it wrote for it. The
latest is the one that every service reads. Each declaration is written on the one that was the latest, and no two
on the same one, so that of two Pyrites that write at once, one writes again on the declaration of the other.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_55, @level1type = N'TABLE', @level1name = N'pyrite_declaration';

DECLARE @pyrite_schema_56 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The pyrite.schema.v1.Declaration, serialized.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_56, @level1type = N'TABLE', @level1name = N'pyrite_declaration', @level2type = N'COLUMN', @level2name = N'declaration';

DECLARE @pyrite_schema_57 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The OpenFGA store of the grants.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_57, @level1type = N'TABLE', @level1name = N'pyrite_declaration', @level2type = N'COLUMN', @level2name = N'store_id';

DECLARE @pyrite_schema_58 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The authorization model that the declaration wrote to the store.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_58, @level1type = N'TABLE', @level1name = N'pyrite_declaration', @level2type = N'COLUMN', @level2name = N'model_id';

DECLARE @pyrite_schema_59 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The id of the declaration that was the latest when this one was written; empty for the first.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_59, @level1type = N'TABLE', @level1name = N'pyrite_declaration', @level2type = N'COLUMN', @level2name = N'previous_id';

CREATE TABLE [pyrite_node] ([name] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [type] NVARCHAR(255) NOT NULL, [parent] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NULL, [create_time] DATETIME2(6) NOT NULL CONSTRAINT [df_pyrite_node_create_time] DEFAULT SYSUTCDATETIME(), CONSTRAINT [pk_pyrite_node] PRIMARY KEY ([name]));

CREATE INDEX [ix_pyrite_node_parent] ON [pyrite_node] ([parent]);

CREATE INDEX [ix_pyrite_node_type] ON [pyrite_node] ([type]);

DECLARE @pyrite_schema_63 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A resource that Pyrite knows: its type and the resource it is under. The application keeps the resource itself;
Pyrite keeps where it sits, so that grants reach it from its parents and nothing is made twice.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_63, @level1type = N'TABLE', @level1name = N'pyrite_node';

DECLARE @pyrite_schema_64 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The resource''s name, such as projects/ledger.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_64, @level1type = N'TABLE', @level1name = N'pyrite_node', @level2type = N'COLUMN', @level2name = N'name';

DECLARE @pyrite_schema_65 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The declared type, such as acme.dev/Project.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_65, @level1type = N'TABLE', @level1name = N'pyrite_node', @level2type = N'COLUMN', @level2name = N'type';

DECLARE @pyrite_schema_66 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The name of the resource it is under; unset for a resource at the top.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_66, @level1type = N'TABLE', @level1name = N'pyrite_node', @level2type = N'COLUMN', @level2name = N'parent';

CREATE TABLE [pyrite_key] ([uid] UNIQUEIDENTIFIER NOT NULL CONSTRAINT [df_pyrite_key_uid] DEFAULT NEWID(), [name] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [parent] NVARCHAR(255) NULL, [display_name] NVARCHAR(255) NULL, [labels] NVARCHAR(MAX) NOT NULL CONSTRAINT [df_pyrite_key_labels] DEFAULT N'{}', [revision] BIGINT NOT NULL CONSTRAINT [df_pyrite_key_revision] DEFAULT 1, [create_time] DATETIME2(6) NOT NULL CONSTRAINT [df_pyrite_key_create_time] DEFAULT SYSUTCDATETIME(), [update_time] DATETIME2(6) NOT NULL CONSTRAINT [df_pyrite_key_update_time] DEFAULT SYSUTCDATETIME(), [delete_time] DATETIME2(6) NULL, [purge_time] DATETIME2(6) NULL, [purpose] NVARCHAR(63) NOT NULL, [algorithm] NVARCHAR(63) NOT NULL, [rotation_period] BIGINT NOT NULL, [next_rotation_time] DATETIME2(6) NULL, [primary_version] NVARCHAR(255) NOT NULL, CONSTRAINT [pk_pyrite_key] PRIMARY KEY ([uid]), CONSTRAINT [ck_pyrite_key_purpose] CHECK (([purpose] IN (N'PURPOSE_UNSPECIFIED', N'ENCRYPTION', N'MAC', N'SIGNING'))), CONSTRAINT [ck_pyrite_key_algorithm] CHECK (([algorithm] IN (N'ALGORITHM_UNSPECIFIED', N'AES256_GCM', N'HMAC_SHA256', N'RS256', N'ES256', N'EDDSA'))));

CREATE UNIQUE INDEX [ux_pyrite_key_name] ON [pyrite_key] ([name]);

CREATE INDEX [ix_pyrite_key_parent] ON [pyrite_key] ([parent]);

DECLARE @pyrite_schema_70 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A key: the resource pyrite.dev/Key, with the standard fields and its own.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_70, @level1type = N'TABLE', @level1name = N'pyrite_key';

DECLARE @pyrite_schema_71 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'How often the key gets a new primary version, in seconds; 0 for never.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_71, @level1type = N'TABLE', @level1name = N'pyrite_key', @level2type = N'COLUMN', @level2name = N'rotation_period';

DECLARE @pyrite_schema_72 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'When the key next gets a new primary version, if it rotates.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_72, @level1type = N'TABLE', @level1name = N'pyrite_key', @level2type = N'COLUMN', @level2name = N'next_rotation_time';

DECLARE @pyrite_schema_73 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The name of the version that encrypts and signs.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_73, @level1type = N'TABLE', @level1name = N'pyrite_key', @level2type = N'COLUMN', @level2name = N'primary_version';

CREATE TABLE [pyrite_key_version] ([uid] UNIQUEIDENTIFIER NOT NULL CONSTRAINT [df_pyrite_key_version_uid] DEFAULT NEWID(), [name] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [parent] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NULL, [display_name] NVARCHAR(255) NULL, [labels] NVARCHAR(MAX) NOT NULL CONSTRAINT [df_pyrite_key_version_labels] DEFAULT N'{}', [revision] BIGINT NOT NULL CONSTRAINT [df_pyrite_key_version_revision] DEFAULT 1, [create_time] DATETIME2(6) NOT NULL CONSTRAINT [df_pyrite_key_version_create_time] DEFAULT SYSUTCDATETIME(), [update_time] DATETIME2(6) NOT NULL CONSTRAINT [df_pyrite_key_version_update_time] DEFAULT SYSUTCDATETIME(), [delete_time] DATETIME2(6) NULL, [purge_time] DATETIME2(6) NULL, [number] BIGINT NOT NULL, [state] NVARCHAR(63) NOT NULL, [algorithm] NVARCHAR(63) NOT NULL, [public_key] NVARCHAR(MAX) NULL, [sealed] VARBINARY(MAX) NOT NULL, [public_key_info] VARBINARY(MAX) NULL, CONSTRAINT [pk_pyrite_key_version] PRIMARY KEY ([uid]), CONSTRAINT [ck_pyrite_key_version_state] CHECK (([state] IN (N'STATE_UNSPECIFIED', N'ENABLED', N'DISABLED'))), CONSTRAINT [ck_pyrite_key_version_algorithm] CHECK (([algorithm] IN (N'ALGORITHM_UNSPECIFIED', N'AES256_GCM', N'HMAC_SHA256', N'RS256', N'ES256', N'EDDSA'))));

CREATE UNIQUE INDEX [ux_pyrite_key_version_name] ON [pyrite_key_version] ([name]);

CREATE UNIQUE INDEX [ux_pyrite_key_version_parent_number] ON [pyrite_key_version] ([parent], [number]);

DECLARE @pyrite_schema_77 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A version of a key: the resource pyrite.dev/KeyVersion, under its key.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_77, @level1type = N'TABLE', @level1name = N'pyrite_key_version';

DECLARE @pyrite_schema_78 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'1 for the first version of the key, and one more for each after it.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_78, @level1type = N'TABLE', @level1name = N'pyrite_key_version', @level2type = N'COLUMN', @level2name = N'number';

DECLARE @pyrite_schema_79 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The public key of a signing version, as a JWK; empty for the others.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_79, @level1type = N'TABLE', @level1name = N'pyrite_key_version', @level2type = N'COLUMN', @level2name = N'public_key';

DECLARE @pyrite_schema_80 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The key material, encrypted by the root key.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_80, @level1type = N'TABLE', @level1name = N'pyrite_key_version', @level2type = N'COLUMN', @level2name = N'sealed';

DECLARE @pyrite_schema_81 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The X.509 SubjectPublicKeyInfo of a signing version; empty for the others.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_81, @level1type = N'TABLE', @level1name = N'pyrite_key_version', @level2type = N'COLUMN', @level2name = N'public_key_info';

ALTER TABLE [pyrite_account] ADD CONSTRAINT [fk_pyrite_account_name] FOREIGN KEY ([name]) REFERENCES [pyrite_node] ([name]) ON DELETE CASCADE;

ALTER TABLE [pyrite_identifier] ADD CONSTRAINT [fk_pyrite_identifier_parent] FOREIGN KEY ([parent]) REFERENCES [pyrite_account] ([name]) ON DELETE CASCADE;

ALTER TABLE [pyrite_factor] ADD CONSTRAINT [fk_pyrite_factor_parent] FOREIGN KEY ([parent]) REFERENCES [pyrite_account] ([name]) ON DELETE CASCADE;

ALTER TABLE [pyrite_session] ADD CONSTRAINT [fk_pyrite_session_parent] FOREIGN KEY ([parent]) REFERENCES [pyrite_account] ([name]) ON DELETE CASCADE;

ALTER TABLE [pyrite_provider_link] ADD CONSTRAINT [fk_pyrite_provider_link_factor] FOREIGN KEY ([factor]) REFERENCES [pyrite_factor] ([name]) ON DELETE CASCADE;

ALTER TABLE [pyrite_refresh_token] ADD CONSTRAINT [fk_pyrite_refresh_token_session] FOREIGN KEY ([session]) REFERENCES [pyrite_session] ([name]) ON DELETE CASCADE;

ALTER TABLE [pyrite_exchange] ADD CONSTRAINT [fk_pyrite_exchange_account] FOREIGN KEY ([account]) REFERENCES [pyrite_account] ([name]) ON DELETE CASCADE;

ALTER TABLE [pyrite_node] ADD CONSTRAINT [fk_pyrite_node_parent] FOREIGN KEY ([parent]) REFERENCES [pyrite_node] ([name]);

ALTER TABLE [pyrite_key] ADD CONSTRAINT [fk_pyrite_key_name] FOREIGN KEY ([name]) REFERENCES [pyrite_node] ([name]) ON DELETE CASCADE;

ALTER TABLE [pyrite_key_version] ADD CONSTRAINT [fk_pyrite_key_version_parent] FOREIGN KEY ([parent]) REFERENCES [pyrite_key] ([name]) ON DELETE CASCADE;
GO

CREATE TRIGGER [tg_pyrite_account_update] ON [pyrite_account] AFTER UPDATE AS
BEGIN
  SET NOCOUNT ON;
  IF TRIGGER_NESTLEVEL(@@PROCID) > 1 RETURN;
  UPDATE [t] SET [revision] = ([d].[revision] + 1), [update_time] = SYSUTCDATETIME() FROM [pyrite_account] AS [t] JOIN inserted AS [i] ON [t].[uid] = [i].[uid] LEFT JOIN deleted AS [d] ON [t].[uid] = [d].[uid];
END;
GO

CREATE TRIGGER [tg_pyrite_identifier_update] ON [pyrite_identifier] AFTER UPDATE AS
BEGIN
  SET NOCOUNT ON;
  IF TRIGGER_NESTLEVEL(@@PROCID) > 1 RETURN;
  UPDATE [t] SET [revision] = ([d].[revision] + 1), [update_time] = SYSUTCDATETIME() FROM [pyrite_identifier] AS [t] JOIN inserted AS [i] ON [t].[uid] = [i].[uid] LEFT JOIN deleted AS [d] ON [t].[uid] = [d].[uid];
END;
GO

CREATE TRIGGER [tg_pyrite_factor_update] ON [pyrite_factor] AFTER UPDATE AS
BEGIN
  SET NOCOUNT ON;
  IF TRIGGER_NESTLEVEL(@@PROCID) > 1 RETURN;
  UPDATE [t] SET [revision] = ([d].[revision] + 1), [update_time] = SYSUTCDATETIME() FROM [pyrite_factor] AS [t] JOIN inserted AS [i] ON [t].[uid] = [i].[uid] LEFT JOIN deleted AS [d] ON [t].[uid] = [d].[uid];
END;
GO

CREATE TRIGGER [tg_pyrite_session_update] ON [pyrite_session] AFTER UPDATE AS
BEGIN
  SET NOCOUNT ON;
  IF TRIGGER_NESTLEVEL(@@PROCID) > 1 RETURN;
  UPDATE [t] SET [revision] = ([d].[revision] + 1), [update_time] = SYSUTCDATETIME() FROM [pyrite_session] AS [t] JOIN inserted AS [i] ON [t].[uid] = [i].[uid] LEFT JOIN deleted AS [d] ON [t].[uid] = [d].[uid];
END;
GO

CREATE TRIGGER [tg_pyrite_key_update] ON [pyrite_key] AFTER UPDATE AS
BEGIN
  SET NOCOUNT ON;
  IF TRIGGER_NESTLEVEL(@@PROCID) > 1 RETURN;
  UPDATE [t] SET [revision] = ([d].[revision] + 1), [update_time] = SYSUTCDATETIME() FROM [pyrite_key] AS [t] JOIN inserted AS [i] ON [t].[uid] = [i].[uid] LEFT JOIN deleted AS [d] ON [t].[uid] = [d].[uid];
END;
GO

CREATE TRIGGER [tg_pyrite_key_version_update] ON [pyrite_key_version] AFTER UPDATE AS
BEGIN
  SET NOCOUNT ON;
  IF TRIGGER_NESTLEVEL(@@PROCID) > 1 RETURN;
  UPDATE [t] SET [revision] = ([d].[revision] + 1), [update_time] = SYSUTCDATETIME() FROM [pyrite_key_version] AS [t] JOIN inserted AS [i] ON [t].[uid] = [i].[uid] LEFT JOIN deleted AS [d] ON [t].[uid] = [d].[uid];
END;
