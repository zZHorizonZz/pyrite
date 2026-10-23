CREATE TABLE "pyrite_account" ("uid" varchar(36) NOT NULL, "name" varchar(255) COLLATE "C" NOT NULL, "parent" varchar(255) NOT NULL, "display_name" varchar(255) NOT NULL, "labels" jsonb NOT NULL, "revision" bigint NOT NULL, "create_time" timestamptz NOT NULL, "update_time" timestamptz NOT NULL, "delete_time" timestamptz, "purge_time" timestamptz, "kind" varchar(16) NOT NULL, CONSTRAINT "pk_pyrite_account" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_pyrite_account_name" ON "pyrite_account" ("name");

COMMENT ON TABLE "pyrite_account" IS 'An account: the resource pyrite.dev/Account, whose principals are user:<id>.';

COMMENT ON COLUMN "pyrite_account"."kind" IS 'HUMAN or SERVICE.';

CREATE TABLE "pyrite_identifier" ("uid" varchar(36) NOT NULL, "name" varchar(255) COLLATE "C" NOT NULL, "parent" varchar(255) NOT NULL, "display_name" varchar(255) NOT NULL, "labels" jsonb NOT NULL, "revision" bigint NOT NULL, "create_time" timestamptz NOT NULL, "update_time" timestamptz NOT NULL, "delete_time" timestamptz, "purge_time" timestamptz, "kind" varchar(16) NOT NULL, "value" varchar(320) COLLATE "C" NOT NULL, "verified" boolean NOT NULL, "primary" boolean NOT NULL, CONSTRAINT "pk_pyrite_identifier" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_pyrite_identifier_name" ON "pyrite_identifier" ("name");

CREATE INDEX "ix_pyrite_identifier_parent" ON "pyrite_identifier" ("parent");

CREATE UNIQUE INDEX "ux_pyrite_identifier_kind_value" ON "pyrite_identifier" ("kind", "value");

COMMENT ON TABLE "pyrite_identifier" IS 'An identifier of an account: the resource pyrite.dev/Identifier, under its account.';

COMMENT ON COLUMN "pyrite_identifier"."kind" IS 'EMAIL, PHONE or USERNAME.';

COMMENT ON COLUMN "pyrite_identifier"."value" IS 'The address, the number in E.164, or the username, as Pyrite normalized it.';

COMMENT ON COLUMN "pyrite_identifier"."primary" IS 'Whether the account prefers it among its identifiers; the caller sets it.';

CREATE TABLE "pyrite_factor" ("uid" varchar(36) NOT NULL, "name" varchar(255) COLLATE "C" NOT NULL, "parent" varchar(255) NOT NULL, "display_name" varchar(255) NOT NULL, "labels" jsonb NOT NULL, "revision" bigint NOT NULL, "create_time" timestamptz NOT NULL, "update_time" timestamptz NOT NULL, "delete_time" timestamptz, "purge_time" timestamptz, "kind" varchar(16) NOT NULL, "state" varchar(16) NOT NULL, "secret" bytea NOT NULL, "credential_id" varchar(255) NOT NULL, "counter" bigint NOT NULL, "user_verified" boolean NOT NULL, CONSTRAINT "pk_pyrite_factor" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_pyrite_factor_name" ON "pyrite_factor" ("name");

CREATE INDEX "ix_pyrite_factor_parent" ON "pyrite_factor" ("parent");

CREATE INDEX "ix_pyrite_factor_credential_id" ON "pyrite_factor" ("credential_id");

COMMENT ON TABLE "pyrite_factor" IS 'A factor of an account: the resource pyrite.dev/Factor, under its account.';

COMMENT ON COLUMN "pyrite_factor"."kind" IS 'PASSWORD, TOTP, PASSKEY or RECOVERY_CODES.';

COMMENT ON COLUMN "pyrite_factor"."state" IS 'PENDING while it is enrolled, then ACTIVE.';

COMMENT ON COLUMN "pyrite_factor"."secret" IS 'The password''s Argon2id hash, the TOTP secret encrypted, the passkey''s credential, or the recovery codes'' MACs.';

COMMENT ON COLUMN "pyrite_factor"."credential_id" IS 'A passkey''s credential id; empty for the others.';

COMMENT ON COLUMN "pyrite_factor"."counter" IS 'A passkey''s signature counter, or the last TOTP step that signed in.';

COMMENT ON COLUMN "pyrite_factor"."user_verified" IS 'Whether a passkey verified its user when it was registered.';

CREATE TABLE "pyrite_session" ("uid" varchar(36) NOT NULL, "name" varchar(255) COLLATE "C" NOT NULL, "parent" varchar(255) NOT NULL, "display_name" varchar(255) NOT NULL, "labels" jsonb NOT NULL, "revision" bigint NOT NULL, "create_time" timestamptz NOT NULL, "update_time" timestamptz NOT NULL, "delete_time" timestamptz, "purge_time" timestamptz, "token_mac" bytea NOT NULL, "expire_time" timestamptz NOT NULL, "idle_expire_time" timestamptz NOT NULL, CONSTRAINT "pk_pyrite_session" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_pyrite_session_name" ON "pyrite_session" ("name");

CREATE INDEX "ix_pyrite_session_parent" ON "pyrite_session" ("parent");

COMMENT ON TABLE "pyrite_session" IS 'A session of an account: the resource pyrite.dev/Session, under its account.';

COMMENT ON COLUMN "pyrite_session"."token_mac" IS 'The MAC of the token''s secret.';

CREATE TABLE "pyrite_flow" ("id" varchar(36) NOT NULL, "definition" varchar(63) NOT NULL, "account" varchar(255) NOT NULL, "identifier" varchar(255) NOT NULL, "revision" bigint NOT NULL, "stage" integer NOT NULL, "proofs" jsonb NOT NULL, "answers" integer NOT NULL, "expire_time" timestamptz NOT NULL, "code_mac" bytea NOT NULL, "code_expire_time" timestamptz, "challenge" bytea NOT NULL, "factor" varchar(255) NOT NULL, "request_id" varchar(64) NOT NULL, "response" bytea NOT NULL, "done" boolean NOT NULL, CONSTRAINT "pk_pyrite_flow" PRIMARY KEY ("id"));

COMMENT ON TABLE "pyrite_flow" IS 'A flow in progress. It is not a resource: only the Authentication service reads and writes it.';

COMMENT ON COLUMN "pyrite_flow"."account" IS 'The account, or empty for a flow that names no account that exists.';

COMMENT ON COLUMN "pyrite_flow"."identifier" IS 'The identifier that the flow started with, if any.';

COMMENT ON COLUMN "pyrite_flow"."stage" IS 'The stage of the definition that the flow is in.';

COMMENT ON COLUMN "pyrite_flow"."proofs" IS 'What the answers of the stage have proven, as JSON.';

COMMENT ON COLUMN "pyrite_flow"."code_mac" IS 'The MAC of the code that was sent, and when it stops working.';

COMMENT ON COLUMN "pyrite_flow"."challenge" IS 'The challenge of a passkey ceremony.';

COMMENT ON COLUMN "pyrite_flow"."factor" IS 'The factor that the flow enrolls, if any.';

COMMENT ON COLUMN "pyrite_flow"."request_id" IS 'The id of the last answer''s request, and the flow that it returned.';

COMMENT ON COLUMN "pyrite_flow"."done" IS 'Whether the flow is done; a done flow takes no answers.';

CREATE TABLE "pyrite_declaration" ("id" varchar(36) NOT NULL, "declaration" bytea NOT NULL, "store_id" varchar(26) NOT NULL, "model_id" varchar(26) NOT NULL, "create_time" timestamptz NOT NULL, CONSTRAINT "pk_pyrite_declaration" PRIMARY KEY ("id"));

CREATE INDEX "ix_pyrite_declaration_create_time" ON "pyrite_declaration" ("create_time");

COMMENT ON TABLE "pyrite_declaration" IS 'A declaration that pyrite.schema.v1.Schema wrote, with the OpenFGA store and model that it wrote for it. The latest
is the one that every service reads.';

COMMENT ON COLUMN "pyrite_declaration"."declaration" IS 'The pyrite.schema.v1.Declaration, serialized.';

COMMENT ON COLUMN "pyrite_declaration"."store_id" IS 'The OpenFGA store of the grants.';

COMMENT ON COLUMN "pyrite_declaration"."model_id" IS 'The authorization model that the declaration wrote to the store.';

CREATE TABLE "pyrite_key" ("uid" varchar(36) NOT NULL, "name" varchar(255) COLLATE "C" NOT NULL, "parent" varchar(255) NOT NULL, "display_name" varchar(255) NOT NULL, "labels" jsonb NOT NULL, "revision" bigint NOT NULL, "create_time" timestamptz NOT NULL, "update_time" timestamptz NOT NULL, "delete_time" timestamptz, "purge_time" timestamptz, "purpose" varchar(16) NOT NULL, "algorithm" varchar(16) NOT NULL, "rotation_period" bigint NOT NULL, "next_rotation_time" timestamptz, "primary_version" varchar(255) NOT NULL, CONSTRAINT "pk_pyrite_key" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_pyrite_key_name" ON "pyrite_key" ("name");

CREATE INDEX "ix_pyrite_key_parent" ON "pyrite_key" ("parent");

COMMENT ON TABLE "pyrite_key" IS 'A key: the resource pyrite.dev/Key, with the standard fields and its own.';

COMMENT ON COLUMN "pyrite_key"."purpose" IS 'ENCRYPTION, MAC or SIGNING.';

COMMENT ON COLUMN "pyrite_key"."algorithm" IS 'AES256_GCM, HMAC_SHA256, RS256, ES256 or EDDSA.';

COMMENT ON COLUMN "pyrite_key"."rotation_period" IS 'How often the key gets a new primary version, in seconds; 0 for never.';

COMMENT ON COLUMN "pyrite_key"."next_rotation_time" IS 'When the key next gets a new primary version, if it rotates.';

COMMENT ON COLUMN "pyrite_key"."primary_version" IS 'The name of the version that encrypts and signs.';

CREATE TABLE "pyrite_key_version" ("uid" varchar(36) NOT NULL, "name" varchar(255) COLLATE "C" NOT NULL, "parent" varchar(255) NOT NULL, "display_name" varchar(255) NOT NULL, "labels" jsonb NOT NULL, "revision" bigint NOT NULL, "create_time" timestamptz NOT NULL, "update_time" timestamptz NOT NULL, "delete_time" timestamptz, "purge_time" timestamptz, "number" bigint NOT NULL, "state" varchar(16) NOT NULL, "algorithm" varchar(16) NOT NULL, "public_key" jsonb NOT NULL, "sealed" bytea NOT NULL, "public_key_info" bytea NOT NULL, CONSTRAINT "pk_pyrite_key_version" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_pyrite_key_version_name" ON "pyrite_key_version" ("name");

CREATE INDEX "ix_pyrite_key_version_parent" ON "pyrite_key_version" ("parent");

COMMENT ON TABLE "pyrite_key_version" IS 'A version of a key: the resource pyrite.dev/KeyVersion, under its key.';

COMMENT ON COLUMN "pyrite_key_version"."number" IS '1 for the first version of the key, and one more for each after it.';

COMMENT ON COLUMN "pyrite_key_version"."state" IS 'ENABLED or DISABLED.';

COMMENT ON COLUMN "pyrite_key_version"."public_key" IS 'The public key of a signing version, as a JWK; empty for the others.';

COMMENT ON COLUMN "pyrite_key_version"."sealed" IS 'The key material, encrypted by the root key.';

COMMENT ON COLUMN "pyrite_key_version"."public_key_info" IS 'The X.509 SubjectPublicKeyInfo of a signing version; empty for the others.';
