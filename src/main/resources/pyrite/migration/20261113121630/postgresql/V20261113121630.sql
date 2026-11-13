CREATE TYPE "account_kind" AS ENUM ('KIND_UNSPECIFIED', 'HUMAN', 'SERVICE');

CREATE TYPE "factor_kind" AS ENUM ('KIND_UNSPECIFIED', 'PASSWORD', 'TOTP', 'PASSKEY', 'RECOVERY_CODES', 'PROVIDER');

CREATE TYPE "factor_state" AS ENUM ('STATE_UNSPECIFIED', 'PENDING', 'ACTIVE');

CREATE TYPE "key_purpose" AS ENUM ('PURPOSE_UNSPECIFIED', 'ENCRYPTION', 'MAC', 'SIGNING');

CREATE TYPE "key_algorithm" AS ENUM ('ALGORITHM_UNSPECIFIED', 'AES256_GCM', 'HMAC_SHA256', 'RS256', 'ES256', 'EDDSA');

CREATE TYPE "key_version_state" AS ENUM ('STATE_UNSPECIFIED', 'ENABLED', 'DISABLED');

CREATE TABLE "pyrite_account" ("uid" uuid NOT NULL DEFAULT gen_random_uuid(), "name" varchar(255) COLLATE "C" NOT NULL, "parent" varchar(255), "display_name" varchar(255), "labels" jsonb NOT NULL DEFAULT '{}', "revision" bigint NOT NULL DEFAULT 1, "create_time" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, "update_time" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, "delete_time" timestamptz, "purge_time" timestamptz, "kind" "account_kind" NOT NULL, CONSTRAINT "pk_pyrite_account" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_pyrite_account_name" ON "pyrite_account" ("name");

COMMENT ON TABLE "pyrite_account" IS 'An account: the resource pyrite.dev/Account, whose principals are user:<id>.';

CREATE TABLE "pyrite_identifier" ("uid" uuid NOT NULL DEFAULT gen_random_uuid(), "name" varchar(255) COLLATE "C" NOT NULL, "parent" varchar(255) COLLATE "C", "display_name" varchar(255), "labels" jsonb NOT NULL DEFAULT '{}', "revision" bigint NOT NULL DEFAULT 1, "create_time" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, "update_time" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, "delete_time" timestamptz, "purge_time" timestamptz, "kind" varchar(16) NOT NULL, "value" varchar(320) COLLATE "C" NOT NULL, "verified" boolean NOT NULL, "primary" boolean NOT NULL, "claim" varchar(340) NOT NULL, CONSTRAINT "pk_pyrite_identifier" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_pyrite_identifier_name" ON "pyrite_identifier" ("name");

CREATE INDEX "ix_pyrite_identifier_parent" ON "pyrite_identifier" ("parent");

CREATE INDEX "ix_pyrite_identifier_kind_value" ON "pyrite_identifier" ("kind", "value");

CREATE UNIQUE INDEX "ux_pyrite_identifier_claim" ON "pyrite_identifier" ("claim");

COMMENT ON TABLE "pyrite_identifier" IS 'An identifier of an account: the resource pyrite.dev/Identifier, under its account.';

COMMENT ON COLUMN "pyrite_identifier"."kind" IS 'EMAIL, PHONE or USERNAME.';

COMMENT ON COLUMN "pyrite_identifier"."value" IS 'The address, the number in E.164, or the username, as Pyrite normalized it.';

COMMENT ON COLUMN "pyrite_identifier"."primary" IS 'Whether the account prefers it among its identifiers; the caller sets it.';

COMMENT ON COLUMN "pyrite_identifier"."claim" IS 'The kind and the value, as email:anne@acme.dev, once the identifier is verified, so that no two verified
identifiers hold one value; the identifier''s own name while it is not verified, so that any account can add a value
without learning or blocking whether another holds it.';

CREATE TABLE "pyrite_factor" ("uid" uuid NOT NULL DEFAULT gen_random_uuid(), "name" varchar(255) COLLATE "C" NOT NULL, "parent" varchar(255) COLLATE "C", "display_name" varchar(255), "labels" jsonb NOT NULL DEFAULT '{}', "revision" bigint NOT NULL DEFAULT 1, "create_time" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, "update_time" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, "delete_time" timestamptz, "purge_time" timestamptz, "kind" "factor_kind" NOT NULL, "state" "factor_state" NOT NULL, "secret" bytea NOT NULL, "credential_id" varchar(255) NOT NULL, "counter" bigint NOT NULL, "user_verified" boolean NOT NULL, CONSTRAINT "pk_pyrite_factor" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_pyrite_factor_name" ON "pyrite_factor" ("name");

CREATE INDEX "ix_pyrite_factor_parent" ON "pyrite_factor" ("parent");

CREATE INDEX "ix_pyrite_factor_credential_id" ON "pyrite_factor" ("credential_id");

COMMENT ON TABLE "pyrite_factor" IS 'A factor of an account: the resource pyrite.dev/Factor, under its account.';

COMMENT ON COLUMN "pyrite_factor"."state" IS 'PENDING while it is enrolled, then ACTIVE.';

COMMENT ON COLUMN "pyrite_factor"."secret" IS 'The password''s Argon2id hash, the TOTP secret encrypted, the passkey''s credential, the recovery codes'' MACs, or the
provider account as its sign-in proved it.';

COMMENT ON COLUMN "pyrite_factor"."credential_id" IS 'A passkey''s credential id, or the provider and the id of a provider account, as google:1093...; empty for the
others.';

COMMENT ON COLUMN "pyrite_factor"."counter" IS 'A passkey''s signature counter, or the last TOTP step that signed in.';

COMMENT ON COLUMN "pyrite_factor"."user_verified" IS 'Whether a passkey verified its user when it was registered.';

CREATE TABLE "pyrite_session" ("uid" uuid NOT NULL DEFAULT gen_random_uuid(), "name" varchar(255) COLLATE "C" NOT NULL, "parent" varchar(255) COLLATE "C", "display_name" varchar(255), "labels" jsonb NOT NULL DEFAULT '{}', "revision" bigint NOT NULL DEFAULT 1, "create_time" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, "update_time" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, "delete_time" timestamptz, "purge_time" timestamptz, "expire_time" timestamptz NOT NULL, "idle_expire_time" timestamptz NOT NULL, CONSTRAINT "pk_pyrite_session" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_pyrite_session_name" ON "pyrite_session" ("name");

CREATE INDEX "ix_pyrite_session_parent" ON "pyrite_session" ("parent");

COMMENT ON TABLE "pyrite_session" IS 'A session of an account: the resource pyrite.dev/Session, under its account.';

CREATE TABLE "pyrite_provider_link" ("factor" varchar(255) COLLATE "C" NOT NULL, "provider" varchar(63) NOT NULL, "account_id" varchar(255) NOT NULL, CONSTRAINT "pk_pyrite_provider_link" PRIMARY KEY ("factor"));

CREATE UNIQUE INDEX "ux_pyrite_provider_link_provider_account_id" ON "pyrite_provider_link" ("provider", "account_id");

COMMENT ON TABLE "pyrite_provider_link" IS 'The account at a provider that a factor of the kind PROVIDER links. The provider and the id are unique together, so
that no account at a provider is linked to two accounts. It goes with its factor.';

COMMENT ON COLUMN "pyrite_provider_link"."factor" IS 'The factor.';

COMMENT ON COLUMN "pyrite_provider_link"."provider" IS 'The name of the provider in the options, such as google.';

COMMENT ON COLUMN "pyrite_provider_link"."account_id" IS 'The id of the account at the provider.';

CREATE TABLE "pyrite_refresh_token" ("id" varchar(36) NOT NULL, "session" varchar(255) COLLATE "C" NOT NULL, "mac" bytea NOT NULL, "create_time" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, "use_time" timestamptz, CONSTRAINT "pk_pyrite_refresh_token" PRIMARY KEY ("id"));

CREATE INDEX "ix_pyrite_refresh_token_session" ON "pyrite_refresh_token" ("session");

COMMENT ON TABLE "pyrite_refresh_token" IS 'A refresh token of a session. A refresh trades it for the next, so a token works once; one that comes back after its
trade ends its session. It goes with its session.';

COMMENT ON COLUMN "pyrite_refresh_token"."id" IS 'The id of the token, before the dot in the token.';

COMMENT ON COLUMN "pyrite_refresh_token"."session" IS 'The session.';

COMMENT ON COLUMN "pyrite_refresh_token"."mac" IS 'The MAC of the token''s secret, which the MAC key of the options signs.';

COMMENT ON COLUMN "pyrite_refresh_token"."use_time" IS 'When a refresh traded the token for the next; unset while it works.';

CREATE TABLE "pyrite_exchange" ("id" varchar(36) NOT NULL, "definition" varchar(63) NOT NULL, "account" varchar(255) COLLATE "C", "identifier" varchar(255), "revision" bigint NOT NULL, "stage" integer NOT NULL, "proofs" jsonb NOT NULL, "answers" integer NOT NULL, "expire_time" timestamptz NOT NULL, "nonce" bytea, "nonce_expire_time" timestamptz, "state" jsonb NOT NULL, "factor" varchar(255), "request_id" varchar(64), "response" bytea, "done" boolean NOT NULL, "provider_account" bytea, CONSTRAINT "pk_pyrite_exchange" PRIMARY KEY ("id"));

COMMENT ON TABLE "pyrite_exchange" IS 'An exchange in progress. It is not a resource: only the Authentication service reads and writes it.';

COMMENT ON COLUMN "pyrite_exchange"."account" IS 'The account; unset for an exchange that names no account that exists.';

COMMENT ON COLUMN "pyrite_exchange"."identifier" IS 'The identifier that the exchange started with, if any.';

COMMENT ON COLUMN "pyrite_exchange"."stage" IS 'The stage of the definition that the exchange is in.';

COMMENT ON COLUMN "pyrite_exchange"."proofs" IS 'What the answers of the stage have proven, as JSON.';

COMMENT ON COLUMN "pyrite_exchange"."nonce" IS 'The random nonce of the stage, which a passkey answer signs, and when it, and what the verifiers keep with it, stop
working.';

COMMENT ON COLUMN "pyrite_exchange"."state" IS 'What the verifiers keep for the stage, such as the MAC of the code that was sent, as JSON by the name of the
answer.';

COMMENT ON COLUMN "pyrite_exchange"."factor" IS 'The factor that the exchange enrolls, if any.';

COMMENT ON COLUMN "pyrite_exchange"."request_id" IS 'The id of the last answer''s request, and the exchange that it returned.';

COMMENT ON COLUMN "pyrite_exchange"."done" IS 'Whether the exchange is done; a done exchange takes no answers.';

COMMENT ON COLUMN "pyrite_exchange"."provider_account" IS 'The account at a provider that the exchange creates an account for, as a ProviderAccount.';

CREATE TABLE "pyrite_declaration" ("id" varchar(36) NOT NULL, "declaration" bytea NOT NULL, "store_id" varchar(26) NOT NULL, "model_id" varchar(26) NOT NULL, "create_time" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, "previous_id" varchar(36) NOT NULL, CONSTRAINT "pk_pyrite_declaration" PRIMARY KEY ("id"));

CREATE INDEX "ix_pyrite_declaration_create_time" ON "pyrite_declaration" ("create_time");

CREATE UNIQUE INDEX "ux_pyrite_declaration_previous_id" ON "pyrite_declaration" ("previous_id");

COMMENT ON TABLE "pyrite_declaration" IS 'A declaration that pyrite.schema.v1.Schema wrote, with the store of grants and the model that it wrote for it. The
latest is the one that every service reads. Each declaration is written on the one that was the latest, and no two
on the same one, so that of two Pyrites that write at once, one writes again on the declaration of the other.';

COMMENT ON COLUMN "pyrite_declaration"."declaration" IS 'The pyrite.schema.v1.Declaration, serialized.';

COMMENT ON COLUMN "pyrite_declaration"."store_id" IS 'The OpenFGA store of the grants.';

COMMENT ON COLUMN "pyrite_declaration"."model_id" IS 'The authorization model that the declaration wrote to the store.';

COMMENT ON COLUMN "pyrite_declaration"."previous_id" IS 'The id of the declaration that was the latest when this one was written; empty for the first.';

CREATE TABLE "pyrite_node" ("name" varchar(255) COLLATE "C" NOT NULL, "type" varchar(255) NOT NULL, "parent" varchar(255) COLLATE "C", "create_time" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, CONSTRAINT "pk_pyrite_node" PRIMARY KEY ("name"));

CREATE INDEX "ix_pyrite_node_parent" ON "pyrite_node" ("parent");

CREATE INDEX "ix_pyrite_node_type" ON "pyrite_node" ("type");

COMMENT ON TABLE "pyrite_node" IS 'A resource that Pyrite knows: its type and the resource it is under. The application keeps the resource itself;
Pyrite keeps where it sits, so that grants reach it from its parents and nothing is made twice.';

COMMENT ON COLUMN "pyrite_node"."name" IS 'The resource''s name, such as projects/ledger.';

COMMENT ON COLUMN "pyrite_node"."type" IS 'The declared type, such as acme.dev/Project.';

COMMENT ON COLUMN "pyrite_node"."parent" IS 'The name of the resource it is under; unset for a resource at the top.';

CREATE TABLE "pyrite_key" ("uid" uuid NOT NULL DEFAULT gen_random_uuid(), "name" varchar(255) COLLATE "C" NOT NULL, "parent" varchar(255), "display_name" varchar(255), "labels" jsonb NOT NULL DEFAULT '{}', "revision" bigint NOT NULL DEFAULT 1, "create_time" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, "update_time" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, "delete_time" timestamptz, "purge_time" timestamptz, "purpose" "key_purpose" NOT NULL, "algorithm" "key_algorithm" NOT NULL, "rotation_period" bigint NOT NULL, "next_rotation_time" timestamptz, "primary_version" varchar(255) NOT NULL, CONSTRAINT "pk_pyrite_key" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_pyrite_key_name" ON "pyrite_key" ("name");

CREATE INDEX "ix_pyrite_key_parent" ON "pyrite_key" ("parent");

COMMENT ON TABLE "pyrite_key" IS 'A key: the resource pyrite.dev/Key, with the standard fields and its own.';

COMMENT ON COLUMN "pyrite_key"."rotation_period" IS 'How often the key gets a new primary version, in seconds; 0 for never.';

COMMENT ON COLUMN "pyrite_key"."next_rotation_time" IS 'When the key next gets a new primary version, if it rotates.';

COMMENT ON COLUMN "pyrite_key"."primary_version" IS 'The name of the version that encrypts and signs.';

CREATE TABLE "pyrite_key_version" ("uid" uuid NOT NULL DEFAULT gen_random_uuid(), "name" varchar(255) COLLATE "C" NOT NULL, "parent" varchar(255) COLLATE "C", "display_name" varchar(255), "labels" jsonb NOT NULL DEFAULT '{}', "revision" bigint NOT NULL DEFAULT 1, "create_time" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, "update_time" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, "delete_time" timestamptz, "purge_time" timestamptz, "number" bigint NOT NULL, "state" "key_version_state" NOT NULL, "algorithm" "key_algorithm" NOT NULL, "public_key" jsonb, "sealed" bytea NOT NULL, "public_key_info" bytea, CONSTRAINT "pk_pyrite_key_version" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_pyrite_key_version_name" ON "pyrite_key_version" ("name");

CREATE UNIQUE INDEX "ux_pyrite_key_version_parent_number" ON "pyrite_key_version" ("parent", "number");

COMMENT ON TABLE "pyrite_key_version" IS 'A version of a key: the resource pyrite.dev/KeyVersion, under its key.';

COMMENT ON COLUMN "pyrite_key_version"."number" IS '1 for the first version of the key, and one more for each after it.';

COMMENT ON COLUMN "pyrite_key_version"."public_key" IS 'The public key of a signing version, as a JWK; empty for the others.';

COMMENT ON COLUMN "pyrite_key_version"."sealed" IS 'The key material, encrypted by the root key.';

COMMENT ON COLUMN "pyrite_key_version"."public_key_info" IS 'The X.509 SubjectPublicKeyInfo of a signing version; empty for the others.';

ALTER TABLE "pyrite_account" ADD CONSTRAINT "fk_pyrite_account_name" FOREIGN KEY ("name") REFERENCES "pyrite_node" ("name") ON DELETE CASCADE;

ALTER TABLE "pyrite_identifier" ADD CONSTRAINT "fk_pyrite_identifier_parent" FOREIGN KEY ("parent") REFERENCES "pyrite_account" ("name") ON DELETE CASCADE;

ALTER TABLE "pyrite_factor" ADD CONSTRAINT "fk_pyrite_factor_parent" FOREIGN KEY ("parent") REFERENCES "pyrite_account" ("name") ON DELETE CASCADE;

ALTER TABLE "pyrite_session" ADD CONSTRAINT "fk_pyrite_session_parent" FOREIGN KEY ("parent") REFERENCES "pyrite_account" ("name") ON DELETE CASCADE;

ALTER TABLE "pyrite_provider_link" ADD CONSTRAINT "fk_pyrite_provider_link_factor" FOREIGN KEY ("factor") REFERENCES "pyrite_factor" ("name") ON DELETE CASCADE;

ALTER TABLE "pyrite_refresh_token" ADD CONSTRAINT "fk_pyrite_refresh_token_session" FOREIGN KEY ("session") REFERENCES "pyrite_session" ("name") ON DELETE CASCADE;

ALTER TABLE "pyrite_exchange" ADD CONSTRAINT "fk_pyrite_exchange_account" FOREIGN KEY ("account") REFERENCES "pyrite_account" ("name") ON DELETE CASCADE;

ALTER TABLE "pyrite_node" ADD CONSTRAINT "fk_pyrite_node_parent" FOREIGN KEY ("parent") REFERENCES "pyrite_node" ("name") ON DELETE RESTRICT;

ALTER TABLE "pyrite_key" ADD CONSTRAINT "fk_pyrite_key_name" FOREIGN KEY ("name") REFERENCES "pyrite_node" ("name") ON DELETE CASCADE;

ALTER TABLE "pyrite_key_version" ADD CONSTRAINT "fk_pyrite_key_version_parent" FOREIGN KEY ("parent") REFERENCES "pyrite_key" ("name") ON DELETE CASCADE;

CREATE FUNCTION "rw_pyrite_account"() RETURNS trigger LANGUAGE plpgsql AS $leadseal$
BEGIN
  NEW."revision" := (OLD."revision" + 1);
  NEW."update_time" := CURRENT_TIMESTAMP;
  RETURN NEW;
END;
$leadseal$;

CREATE TRIGGER "rw_pyrite_account" BEFORE UPDATE ON "pyrite_account" FOR EACH ROW EXECUTE FUNCTION "rw_pyrite_account"();

CREATE FUNCTION "rw_pyrite_identifier"() RETURNS trigger LANGUAGE plpgsql AS $leadseal$
BEGIN
  NEW."revision" := (OLD."revision" + 1);
  NEW."update_time" := CURRENT_TIMESTAMP;
  RETURN NEW;
END;
$leadseal$;

CREATE TRIGGER "rw_pyrite_identifier" BEFORE UPDATE ON "pyrite_identifier" FOR EACH ROW EXECUTE FUNCTION "rw_pyrite_identifier"();

CREATE FUNCTION "rw_pyrite_factor"() RETURNS trigger LANGUAGE plpgsql AS $leadseal$
BEGIN
  NEW."revision" := (OLD."revision" + 1);
  NEW."update_time" := CURRENT_TIMESTAMP;
  RETURN NEW;
END;
$leadseal$;

CREATE TRIGGER "rw_pyrite_factor" BEFORE UPDATE ON "pyrite_factor" FOR EACH ROW EXECUTE FUNCTION "rw_pyrite_factor"();

CREATE FUNCTION "rw_pyrite_session"() RETURNS trigger LANGUAGE plpgsql AS $leadseal$
BEGIN
  NEW."revision" := (OLD."revision" + 1);
  NEW."update_time" := CURRENT_TIMESTAMP;
  RETURN NEW;
END;
$leadseal$;

CREATE TRIGGER "rw_pyrite_session" BEFORE UPDATE ON "pyrite_session" FOR EACH ROW EXECUTE FUNCTION "rw_pyrite_session"();

CREATE FUNCTION "rw_pyrite_key"() RETURNS trigger LANGUAGE plpgsql AS $leadseal$
BEGIN
  NEW."revision" := (OLD."revision" + 1);
  NEW."update_time" := CURRENT_TIMESTAMP;
  RETURN NEW;
END;
$leadseal$;

CREATE TRIGGER "rw_pyrite_key" BEFORE UPDATE ON "pyrite_key" FOR EACH ROW EXECUTE FUNCTION "rw_pyrite_key"();

CREATE FUNCTION "rw_pyrite_key_version"() RETURNS trigger LANGUAGE plpgsql AS $leadseal$
BEGIN
  NEW."revision" := (OLD."revision" + 1);
  NEW."update_time" := CURRENT_TIMESTAMP;
  RETURN NEW;
END;
$leadseal$;

CREATE TRIGGER "rw_pyrite_key_version" BEFORE UPDATE ON "pyrite_key_version" FOR EACH ROW EXECUTE FUNCTION "rw_pyrite_key_version"();
