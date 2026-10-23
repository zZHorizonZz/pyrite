ALTER TABLE "pyrite_account" ALTER COLUMN "parent" DROP NOT NULL;

ALTER TABLE "pyrite_account" ALTER COLUMN "display_name" DROP NOT NULL;

ALTER TABLE "pyrite_identifier" ALTER COLUMN "parent" DROP NOT NULL;

ALTER TABLE "pyrite_identifier" ALTER COLUMN "display_name" DROP NOT NULL;

ALTER TABLE "pyrite_factor" ALTER COLUMN "parent" DROP NOT NULL;

ALTER TABLE "pyrite_factor" ALTER COLUMN "display_name" DROP NOT NULL;

ALTER TABLE "pyrite_session" ALTER COLUMN "parent" DROP NOT NULL;

ALTER TABLE "pyrite_session" ALTER COLUMN "display_name" DROP NOT NULL;

ALTER TABLE "pyrite_flow" ALTER COLUMN "account" DROP NOT NULL;

ALTER TABLE "pyrite_flow" ALTER COLUMN "identifier" DROP NOT NULL;

ALTER TABLE "pyrite_flow" ALTER COLUMN "code_mac" DROP NOT NULL;

ALTER TABLE "pyrite_flow" ALTER COLUMN "challenge" DROP NOT NULL;

ALTER TABLE "pyrite_flow" ALTER COLUMN "factor" DROP NOT NULL;

ALTER TABLE "pyrite_flow" ALTER COLUMN "request_id" DROP NOT NULL;

ALTER TABLE "pyrite_flow" ALTER COLUMN "response" DROP NOT NULL;

ALTER TABLE "pyrite_key" ALTER COLUMN "parent" DROP NOT NULL;

ALTER TABLE "pyrite_key" ALTER COLUMN "display_name" DROP NOT NULL;

ALTER TABLE "pyrite_key_version" ALTER COLUMN "parent" DROP NOT NULL;

ALTER TABLE "pyrite_key_version" ALTER COLUMN "display_name" DROP NOT NULL;

ALTER TABLE "pyrite_key_version" ALTER COLUMN "public_key" DROP NOT NULL;

ALTER TABLE "pyrite_key_version" ALTER COLUMN "public_key_info" DROP NOT NULL;

COMMENT ON COLUMN "pyrite_flow"."account" IS 'The account; unset for a flow that names no account that exists.';
