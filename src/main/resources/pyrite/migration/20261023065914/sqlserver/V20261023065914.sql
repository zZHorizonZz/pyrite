CREATE TABLE [pyrite_account] ([uid] NVARCHAR(36) NOT NULL, [name] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [parent] NVARCHAR(255) NOT NULL, [display_name] NVARCHAR(255) NOT NULL, [labels] NVARCHAR(MAX) NOT NULL, [revision] BIGINT NOT NULL, [create_time] DATETIME2(6) NOT NULL, [update_time] DATETIME2(6) NOT NULL, [delete_time] DATETIME2(6) NULL, [purge_time] DATETIME2(6) NULL, [kind] NVARCHAR(16) NOT NULL, CONSTRAINT [pk_pyrite_account] PRIMARY KEY ([uid]));

CREATE UNIQUE INDEX [ux_pyrite_account_name] ON [pyrite_account] ([name]);

DECLARE @pyrite_schema_2 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'An account: the resource pyrite.dev/Account, whose principals are user:<id>.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_2, @level1type = N'TABLE', @level1name = N'pyrite_account';

DECLARE @pyrite_schema_3 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'HUMAN or SERVICE.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_3, @level1type = N'TABLE', @level1name = N'pyrite_account', @level2type = N'COLUMN', @level2name = N'kind';

CREATE TABLE [pyrite_identifier] ([uid] NVARCHAR(36) NOT NULL, [name] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [parent] NVARCHAR(255) NOT NULL, [display_name] NVARCHAR(255) NOT NULL, [labels] NVARCHAR(MAX) NOT NULL, [revision] BIGINT NOT NULL, [create_time] DATETIME2(6) NOT NULL, [update_time] DATETIME2(6) NOT NULL, [delete_time] DATETIME2(6) NULL, [purge_time] DATETIME2(6) NULL, [kind] NVARCHAR(16) NOT NULL, [value] NVARCHAR(320) COLLATE Latin1_General_100_BIN2 NOT NULL, [verified] BIT NOT NULL, [primary] BIT NOT NULL, CONSTRAINT [pk_pyrite_identifier] PRIMARY KEY ([uid]));

CREATE UNIQUE INDEX [ux_pyrite_identifier_name] ON [pyrite_identifier] ([name]);

CREATE INDEX [ix_pyrite_identifier_parent] ON [pyrite_identifier] ([parent]);

CREATE UNIQUE INDEX [ux_pyrite_identifier_kind_value] ON [pyrite_identifier] ([kind], [value]);

DECLARE @pyrite_schema_8 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'An identifier of an account: the resource pyrite.dev/Identifier, under its account.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_8, @level1type = N'TABLE', @level1name = N'pyrite_identifier';

DECLARE @pyrite_schema_9 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'EMAIL, PHONE or USERNAME.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_9, @level1type = N'TABLE', @level1name = N'pyrite_identifier', @level2type = N'COLUMN', @level2name = N'kind';

DECLARE @pyrite_schema_10 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The address, the number in E.164, or the username, as Pyrite normalized it.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_10, @level1type = N'TABLE', @level1name = N'pyrite_identifier', @level2type = N'COLUMN', @level2name = N'value';

DECLARE @pyrite_schema_11 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'Whether the account prefers it among its identifiers; the caller sets it.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_11, @level1type = N'TABLE', @level1name = N'pyrite_identifier', @level2type = N'COLUMN', @level2name = N'primary';

CREATE TABLE [pyrite_factor] ([uid] NVARCHAR(36) NOT NULL, [name] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [parent] NVARCHAR(255) NOT NULL, [display_name] NVARCHAR(255) NOT NULL, [labels] NVARCHAR(MAX) NOT NULL, [revision] BIGINT NOT NULL, [create_time] DATETIME2(6) NOT NULL, [update_time] DATETIME2(6) NOT NULL, [delete_time] DATETIME2(6) NULL, [purge_time] DATETIME2(6) NULL, [kind] NVARCHAR(16) NOT NULL, [state] NVARCHAR(16) NOT NULL, [secret] VARBINARY(MAX) NOT NULL, [credential_id] NVARCHAR(255) NOT NULL, [counter] BIGINT NOT NULL, [user_verified] BIT NOT NULL, CONSTRAINT [pk_pyrite_factor] PRIMARY KEY ([uid]));

CREATE UNIQUE INDEX [ux_pyrite_factor_name] ON [pyrite_factor] ([name]);

CREATE INDEX [ix_pyrite_factor_parent] ON [pyrite_factor] ([parent]);

CREATE INDEX [ix_pyrite_factor_credential_id] ON [pyrite_factor] ([credential_id]);

DECLARE @pyrite_schema_16 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A factor of an account: the resource pyrite.dev/Factor, under its account.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_16, @level1type = N'TABLE', @level1name = N'pyrite_factor';

DECLARE @pyrite_schema_17 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'PASSWORD, TOTP, PASSKEY or RECOVERY_CODES.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_17, @level1type = N'TABLE', @level1name = N'pyrite_factor', @level2type = N'COLUMN', @level2name = N'kind';

DECLARE @pyrite_schema_18 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'PENDING while it is enrolled, then ACTIVE.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_18, @level1type = N'TABLE', @level1name = N'pyrite_factor', @level2type = N'COLUMN', @level2name = N'state';

DECLARE @pyrite_schema_19 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The password''s Argon2id hash, the TOTP secret encrypted, the passkey''s credential, or the recovery codes'' MACs.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_19, @level1type = N'TABLE', @level1name = N'pyrite_factor', @level2type = N'COLUMN', @level2name = N'secret';

DECLARE @pyrite_schema_20 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A passkey''s credential id; empty for the others.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_20, @level1type = N'TABLE', @level1name = N'pyrite_factor', @level2type = N'COLUMN', @level2name = N'credential_id';

DECLARE @pyrite_schema_21 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A passkey''s signature counter, or the last TOTP step that signed in.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_21, @level1type = N'TABLE', @level1name = N'pyrite_factor', @level2type = N'COLUMN', @level2name = N'counter';

DECLARE @pyrite_schema_22 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'Whether a passkey verified its user when it was registered.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_22, @level1type = N'TABLE', @level1name = N'pyrite_factor', @level2type = N'COLUMN', @level2name = N'user_verified';

CREATE TABLE [pyrite_session] ([uid] NVARCHAR(36) NOT NULL, [name] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [parent] NVARCHAR(255) NOT NULL, [display_name] NVARCHAR(255) NOT NULL, [labels] NVARCHAR(MAX) NOT NULL, [revision] BIGINT NOT NULL, [create_time] DATETIME2(6) NOT NULL, [update_time] DATETIME2(6) NOT NULL, [delete_time] DATETIME2(6) NULL, [purge_time] DATETIME2(6) NULL, [token_mac] VARBINARY(MAX) NOT NULL, [expire_time] DATETIME2(6) NOT NULL, [idle_expire_time] DATETIME2(6) NOT NULL, CONSTRAINT [pk_pyrite_session] PRIMARY KEY ([uid]));

CREATE UNIQUE INDEX [ux_pyrite_session_name] ON [pyrite_session] ([name]);

CREATE INDEX [ix_pyrite_session_parent] ON [pyrite_session] ([parent]);

DECLARE @pyrite_schema_26 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A session of an account: the resource pyrite.dev/Session, under its account.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_26, @level1type = N'TABLE', @level1name = N'pyrite_session';

DECLARE @pyrite_schema_27 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The MAC of the token''s secret.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_27, @level1type = N'TABLE', @level1name = N'pyrite_session', @level2type = N'COLUMN', @level2name = N'token_mac';

CREATE TABLE [pyrite_flow] ([id] NVARCHAR(36) NOT NULL, [definition] NVARCHAR(63) NOT NULL, [account] NVARCHAR(255) NOT NULL, [identifier] NVARCHAR(255) NOT NULL, [revision] BIGINT NOT NULL, [stage] INT NOT NULL, [proofs] NVARCHAR(MAX) NOT NULL, [answers] INT NOT NULL, [expire_time] DATETIME2(6) NOT NULL, [code_mac] VARBINARY(MAX) NOT NULL, [code_expire_time] DATETIME2(6) NULL, [challenge] VARBINARY(MAX) NOT NULL, [factor] NVARCHAR(255) NOT NULL, [request_id] NVARCHAR(64) NOT NULL, [response] VARBINARY(MAX) NOT NULL, [done] BIT NOT NULL, CONSTRAINT [pk_pyrite_flow] PRIMARY KEY ([id]));

DECLARE @pyrite_schema_29 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A flow in progress. It is not a resource: only the Authentication service reads and writes it.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_29, @level1type = N'TABLE', @level1name = N'pyrite_flow';

DECLARE @pyrite_schema_30 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The account, or empty for a flow that names no account that exists.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_30, @level1type = N'TABLE', @level1name = N'pyrite_flow', @level2type = N'COLUMN', @level2name = N'account';

DECLARE @pyrite_schema_31 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The identifier that the flow started with, if any.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_31, @level1type = N'TABLE', @level1name = N'pyrite_flow', @level2type = N'COLUMN', @level2name = N'identifier';

DECLARE @pyrite_schema_32 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The stage of the definition that the flow is in.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_32, @level1type = N'TABLE', @level1name = N'pyrite_flow', @level2type = N'COLUMN', @level2name = N'stage';

DECLARE @pyrite_schema_33 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'What the answers of the stage have proven, as JSON.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_33, @level1type = N'TABLE', @level1name = N'pyrite_flow', @level2type = N'COLUMN', @level2name = N'proofs';

DECLARE @pyrite_schema_34 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The MAC of the code that was sent, and when it stops working.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_34, @level1type = N'TABLE', @level1name = N'pyrite_flow', @level2type = N'COLUMN', @level2name = N'code_mac';

DECLARE @pyrite_schema_35 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The challenge of a passkey ceremony.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_35, @level1type = N'TABLE', @level1name = N'pyrite_flow', @level2type = N'COLUMN', @level2name = N'challenge';

DECLARE @pyrite_schema_36 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The factor that the flow enrolls, if any.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_36, @level1type = N'TABLE', @level1name = N'pyrite_flow', @level2type = N'COLUMN', @level2name = N'factor';

DECLARE @pyrite_schema_37 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The id of the last answer''s request, and the flow that it returned.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_37, @level1type = N'TABLE', @level1name = N'pyrite_flow', @level2type = N'COLUMN', @level2name = N'request_id';

DECLARE @pyrite_schema_38 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'Whether the flow is done; a done flow takes no answers.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_38, @level1type = N'TABLE', @level1name = N'pyrite_flow', @level2type = N'COLUMN', @level2name = N'done';

CREATE TABLE [pyrite_declaration] ([id] NVARCHAR(36) NOT NULL, [declaration] VARBINARY(MAX) NOT NULL, [store_id] NVARCHAR(26) NOT NULL, [model_id] NVARCHAR(26) NOT NULL, [create_time] DATETIME2(6) NOT NULL, CONSTRAINT [pk_pyrite_declaration] PRIMARY KEY ([id]));

CREATE INDEX [ix_pyrite_declaration_create_time] ON [pyrite_declaration] ([create_time]);

DECLARE @pyrite_schema_41 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A declaration that pyrite.schema.v1.Schema wrote, with the OpenFGA store and model that it wrote for it. The latest
is the one that every service reads.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_41, @level1type = N'TABLE', @level1name = N'pyrite_declaration';

DECLARE @pyrite_schema_42 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The pyrite.schema.v1.Declaration, serialized.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_42, @level1type = N'TABLE', @level1name = N'pyrite_declaration', @level2type = N'COLUMN', @level2name = N'declaration';

DECLARE @pyrite_schema_43 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The OpenFGA store of the grants.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_43, @level1type = N'TABLE', @level1name = N'pyrite_declaration', @level2type = N'COLUMN', @level2name = N'store_id';

DECLARE @pyrite_schema_44 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The authorization model that the declaration wrote to the store.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_44, @level1type = N'TABLE', @level1name = N'pyrite_declaration', @level2type = N'COLUMN', @level2name = N'model_id';

CREATE TABLE [pyrite_key] ([uid] NVARCHAR(36) NOT NULL, [name] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [parent] NVARCHAR(255) NOT NULL, [display_name] NVARCHAR(255) NOT NULL, [labels] NVARCHAR(MAX) NOT NULL, [revision] BIGINT NOT NULL, [create_time] DATETIME2(6) NOT NULL, [update_time] DATETIME2(6) NOT NULL, [delete_time] DATETIME2(6) NULL, [purge_time] DATETIME2(6) NULL, [purpose] NVARCHAR(16) NOT NULL, [algorithm] NVARCHAR(16) NOT NULL, [rotation_period] BIGINT NOT NULL, [next_rotation_time] DATETIME2(6) NULL, [primary_version] NVARCHAR(255) NOT NULL, CONSTRAINT [pk_pyrite_key] PRIMARY KEY ([uid]));

CREATE UNIQUE INDEX [ux_pyrite_key_name] ON [pyrite_key] ([name]);

CREATE INDEX [ix_pyrite_key_parent] ON [pyrite_key] ([parent]);

DECLARE @pyrite_schema_48 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A key: the resource pyrite.dev/Key, with the standard fields and its own.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_48, @level1type = N'TABLE', @level1name = N'pyrite_key';

DECLARE @pyrite_schema_49 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'ENCRYPTION, MAC or SIGNING.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_49, @level1type = N'TABLE', @level1name = N'pyrite_key', @level2type = N'COLUMN', @level2name = N'purpose';

DECLARE @pyrite_schema_50 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'AES256_GCM, HMAC_SHA256, RS256, ES256 or EDDSA.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_50, @level1type = N'TABLE', @level1name = N'pyrite_key', @level2type = N'COLUMN', @level2name = N'algorithm';

DECLARE @pyrite_schema_51 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'How often the key gets a new primary version, in seconds; 0 for never.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_51, @level1type = N'TABLE', @level1name = N'pyrite_key', @level2type = N'COLUMN', @level2name = N'rotation_period';

DECLARE @pyrite_schema_52 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'When the key next gets a new primary version, if it rotates.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_52, @level1type = N'TABLE', @level1name = N'pyrite_key', @level2type = N'COLUMN', @level2name = N'next_rotation_time';

DECLARE @pyrite_schema_53 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The name of the version that encrypts and signs.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_53, @level1type = N'TABLE', @level1name = N'pyrite_key', @level2type = N'COLUMN', @level2name = N'primary_version';

CREATE TABLE [pyrite_key_version] ([uid] NVARCHAR(36) NOT NULL, [name] NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL, [parent] NVARCHAR(255) NOT NULL, [display_name] NVARCHAR(255) NOT NULL, [labels] NVARCHAR(MAX) NOT NULL, [revision] BIGINT NOT NULL, [create_time] DATETIME2(6) NOT NULL, [update_time] DATETIME2(6) NOT NULL, [delete_time] DATETIME2(6) NULL, [purge_time] DATETIME2(6) NULL, [number] BIGINT NOT NULL, [state] NVARCHAR(16) NOT NULL, [algorithm] NVARCHAR(16) NOT NULL, [public_key] NVARCHAR(MAX) NOT NULL, [sealed] VARBINARY(MAX) NOT NULL, [public_key_info] VARBINARY(MAX) NOT NULL, CONSTRAINT [pk_pyrite_key_version] PRIMARY KEY ([uid]));

CREATE UNIQUE INDEX [ux_pyrite_key_version_name] ON [pyrite_key_version] ([name]);

CREATE INDEX [ix_pyrite_key_version_parent] ON [pyrite_key_version] ([parent]);

DECLARE @pyrite_schema_57 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A version of a key: the resource pyrite.dev/KeyVersion, under its key.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_57, @level1type = N'TABLE', @level1name = N'pyrite_key_version';

DECLARE @pyrite_schema_58 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'1 for the first version of the key, and one more for each after it.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_58, @level1type = N'TABLE', @level1name = N'pyrite_key_version', @level2type = N'COLUMN', @level2name = N'number';

DECLARE @pyrite_schema_59 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'ENABLED or DISABLED.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_59, @level1type = N'TABLE', @level1name = N'pyrite_key_version', @level2type = N'COLUMN', @level2name = N'state';

DECLARE @pyrite_schema_60 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The public key of a signing version, as a JWK; empty for the others.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_60, @level1type = N'TABLE', @level1name = N'pyrite_key_version', @level2type = N'COLUMN', @level2name = N'public_key';

DECLARE @pyrite_schema_61 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The key material, encrypted by the root key.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_61, @level1type = N'TABLE', @level1name = N'pyrite_key_version', @level2type = N'COLUMN', @level2name = N'sealed';

DECLARE @pyrite_schema_62 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The X.509 SubjectPublicKeyInfo of a signing version; empty for the others.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_62, @level1type = N'TABLE', @level1name = N'pyrite_key_version', @level2type = N'COLUMN', @level2name = N'public_key_info';
