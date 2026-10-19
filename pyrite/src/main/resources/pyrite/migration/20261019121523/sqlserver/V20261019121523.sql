CREATE TABLE [pyrite_vault] ([name] NVARCHAR(16) NOT NULL, [sealed] VARBINARY(MAX) NOT NULL, [create_time] DATETIME2(6) NOT NULL CONSTRAINT [df_pyrite_vault_create_time] DEFAULT SYSUTCDATETIME(), CONSTRAINT [pk_pyrite_vault] PRIMARY KEY ([name]));

DECLARE @pyrite_schema_1 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The vault key, encrypted under the root key. It encrypts the material of every version, so rotating the root key
encrypts this one row again.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_1, @level1type = N'TABLE', @level1name = N'pyrite_vault';

CREATE TABLE [pyrite_key] ([name] NVARCHAR(63) COLLATE Latin1_General_100_BIN2 NOT NULL, [purpose] NVARCHAR(16) NOT NULL, [algorithm] NVARCHAR(16) NULL, [ahead_millis] BIGINT NOT NULL, [kept_millis] BIGINT NOT NULL, [rotation_millis] BIGINT NOT NULL, [revision] BIGINT NOT NULL, [create_time] DATETIME2(6) NOT NULL CONSTRAINT [df_pyrite_key_create_time] DEFAULT SYSUTCDATETIME(), CONSTRAINT [pk_pyrite_key] PRIMARY KEY ([name]));

DECLARE @pyrite_schema_3 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A key. Every change of the key or of one of its versions moves its revision.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_3, @level1type = N'TABLE', @level1name = N'pyrite_key';

DECLARE @pyrite_schema_4 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'ENCRYPTION, MAC or SIGNING.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_4, @level1type = N'TABLE', @level1name = N'pyrite_key', @level2type = N'COLUMN', @level2name = N'purpose';

DECLARE @pyrite_schema_5 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The algorithm of a signing key: RS256, ES256 or EDDSA.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_5, @level1type = N'TABLE', @level1name = N'pyrite_key', @level2type = N'COLUMN', @level2name = N'algorithm';

DECLARE @pyrite_schema_6 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'How long a signing key publishes a version before it signs, in milliseconds.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_6, @level1type = N'TABLE', @level1name = N'pyrite_key', @level2type = N'COLUMN', @level2name = N'ahead_millis';

DECLARE @pyrite_schema_7 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'How long a signing key keeps a retired version published, in milliseconds.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_7, @level1type = N'TABLE', @level1name = N'pyrite_key', @level2type = N'COLUMN', @level2name = N'kept_millis';

DECLARE @pyrite_schema_8 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'How often the key gets a new primary version, in milliseconds; 0 for never.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_8, @level1type = N'TABLE', @level1name = N'pyrite_key', @level2type = N'COLUMN', @level2name = N'rotation_millis';

CREATE TABLE [pyrite_version] ([key_name] NVARCHAR(63) COLLATE Latin1_General_100_BIN2 NOT NULL, [number] BIGINT NOT NULL, [origin] NVARCHAR(16) NOT NULL, [state] NVARCHAR(16) NOT NULL, [state_time] DATETIME2(6) NULL, [create_time] DATETIME2(6) NOT NULL CONSTRAINT [df_pyrite_version_create_time] DEFAULT SYSUTCDATETIME(), [sealed] VARBINARY(MAX) NULL, [public_key] VARBINARY(MAX) NULL, [certificate] VARBINARY(MAX) NULL, CONSTRAINT [pk_pyrite_version] PRIMARY KEY ([key_name], [number]));

DECLARE @pyrite_schema_10 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'A version of a key.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_10, @level1type = N'TABLE', @level1name = N'pyrite_version';

DECLARE @pyrite_schema_11 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The number of the version in its key: 1 for the first, and one more for each after it.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_11, @level1type = N'TABLE', @level1name = N'pyrite_version', @level2type = N'COLUMN', @level2name = N'number';

DECLARE @pyrite_schema_12 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'GENERATED, EXPORTED or UPLOADED.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_12, @level1type = N'TABLE', @level1name = N'pyrite_version', @level2type = N'COLUMN', @level2name = N'origin';

DECLARE @pyrite_schema_13 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'PENDING, PRIMARY, LIVE, DISABLED or DESTROYED.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_13, @level1type = N'TABLE', @level1name = N'pyrite_version', @level2type = N'COLUMN', @level2name = N'state';

DECLARE @pyrite_schema_14 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'When a pending version becomes primary, since when a primary is primary, until when a live signing version stays
published, or when a version was disabled or destroyed. A live version without it stays published.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_14, @level1type = N'TABLE', @level1name = N'pyrite_version', @level2type = N'COLUMN', @level2name = N'state_time';

DECLARE @pyrite_schema_15 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The secret material, encrypted under the vault key: the AES or HMAC key, or the PKCS#8 private key. None for a
public key alone and for a destroyed version.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_15, @level1type = N'TABLE', @level1name = N'pyrite_version', @level2type = N'COLUMN', @level2name = N'sealed';

DECLARE @pyrite_schema_16 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The X.509 SubjectPublicKeyInfo of a signing version.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_16, @level1type = N'TABLE', @level1name = N'pyrite_version', @level2type = N'COLUMN', @level2name = N'public_key';

DECLARE @pyrite_schema_17 sysname = SCHEMA_NAME() EXEC sp_addextendedproperty @name = N'MS_Description', @value = N'The self-signed X.509 certificate of a signing version, in DER.', @level0type = N'SCHEMA', @level0name = @pyrite_schema_17, @level1type = N'TABLE', @level1name = N'pyrite_version', @level2type = N'COLUMN', @level2name = N'certificate';

ALTER TABLE [pyrite_version] ADD CONSTRAINT [fk_pyrite_version_key_name] FOREIGN KEY ([key_name]) REFERENCES [pyrite_key] ([name]) ON DELETE CASCADE;
